#!/usr/bin/env node
/**
 * Mock OpenAI / OpenAI-compatible /v1/chat/completions SSE server.
 *
 * 路由策略（按用户最后一条消息文本）：
 *   - 包含「时间」/「几点」/「time」 → 触发 server_time tool 调用 → 回灌后吐出时间
 *   - 包含「抓」/「example」/「fetch」/「og」  → 触发 http_fetch tool 调用 → 回灌后写摘要
 *   - 包含「ls」/「shell」/「跑命令」 → 触发 shell_exec tool 调用
 *   - 包含「插件」 → 询问插件状态（无工具，纯文本测试）
 *   - 其它 → 纯文本「你好…」流式输出（默认聊天回归测试用）
 *
 * 启动：node mock-openai-server.mjs [--port 18800]
 */

import http from 'node:http';

const PORT = Number(process.argv.find(a => a.startsWith('--port='))?.split('=')[1]
                || process.env.MOCK_PORT
                || 18800);

/* ---------------- SSE helpers ---------------- */

function sseChunk(res, payload) {
  res.write(`data: ${JSON.stringify(payload)}\n\n`);
}
function sseDone(res) {
  res.write('data: [DONE]\n\n');
  res.end();
}
function delay(ms) { return new Promise(r => setTimeout(r, ms)); }

function makeId() { return 'chatcmpl-' + Math.random().toString(36).slice(2, 12); }
function makeToolId() { return 'call_' + Math.random().toString(36).slice(2, 12); }

function textChunk(id, model, content) {
  return {
    id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model,
    choices: [{ index: 0, delta: { content }, finish_reason: null }],
  };
}
function finishChunk(id, model, reason) {
  return {
    id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model,
    choices: [{ index: 0, delta: {}, finish_reason: reason }],
  };
}
// thinking 模式：模型先吐 reasoning_content（思维链），再吐 content / tool_calls
function reasoningChunk(id, model, reasoning) {
  return {
    id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model,
    choices: [{ index: 0, delta: { reasoning_content: reasoning }, finish_reason: null }],
  };
}
function toolCallChunk(id, model, callId, fnName, argsJson, idx = 0) {
  // DEBUG: MOCK_DROP_TOOL_ID=1 → simulate DeepSeek-like SSE that omits id
  const dropId = process.env.MOCK_DROP_TOOL_ID === '1';
  const entry = dropId
    ? { index: idx, type: 'function', function: { name: fnName, arguments: argsJson } }
    : { index: idx, id: callId, type: 'function', function: { name: fnName, arguments: argsJson } };
  return {
    id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model,
    choices: [{
      index: 0,
      delta: { tool_calls: [entry] },
      finish_reason: null,
    }],
  };
}

/* ---------------- Routing ---------------- */

function lastUserMessage(messages) {
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].role === 'user') {
      const c = messages[i].content;
      if (typeof c === 'string') return c;
      if (Array.isArray(c)) {
        for (const part of c) {
          if (part?.type === 'text' && typeof part.text === 'string') return part.text;
        }
      }
    }
  }
  return '';
}

function hasToolMessage(messages) {
  return messages.some(m => m.role === 'tool');
}

function systemText(messages) {
  return messages.filter(m => m.role === 'system')
    .map(m => (typeof m.content === 'string' ? m.content : JSON.stringify(m.content)))
    .join('\n');
}

function messagesHaveImage(messages) {
  return messages.some(m => Array.isArray(m.content)
    && m.content.some(p => p?.type === 'image_url' || p?.type === 'image'));
}

/* ---------------- usage（token 用量） ---------------- */

function estTokens(str) {
  return Math.max(1, Math.ceil((str || '').length / 4));
}
function promptTokensOf(messages) {
  let n = 0;
  for (const m of messages) {
    const c = m.content;
    if (typeof c === 'string') n += estTokens(c);
    else if (Array.isArray(c)) for (const p of c) {
      if (p?.type === 'text') n += estTokens(p.text);
      else n += 200; // 图片按固定成本
    }
  }
  return n;
}
function buildUsage(messages, outputText) {
  const prompt = promptTokensOf(messages);
  const completion = estTokens(outputText);
  // 模拟 deepseek 风格的缓存命中：约一半输入命中缓存，验证分级计费
  const cacheHit = Math.floor(prompt / 2);
  return {
    prompt_tokens: prompt,
    prompt_cache_hit_tokens: cacheHit,
    prompt_cache_miss_tokens: prompt - cacheHit,
    completion_tokens: completion,
    total_tokens: prompt + completion,
  };
}
function usageChunk(id, model, usage) {
  // OpenAI 兼容协议：include_usage 末帧 choices 为空、顶层带 usage
  return { id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model, choices: [], usage };
}

/**
 * 按真实 DeepSeek V4 (deepseek-v4-flash/pro) 的请求体契约校验回灌消息。
 * 返回错误字符串表示违约（mock 会回 400），返回 null 表示通过。
 *
 * 契约来源：https://api-docs.deepseek.com/guides/thinking_mode
 *           https://api-docs.deepseek.com/guides/function_calling
 *  1. 每条 role=tool 的消息必须有非空 tool_call_id
 *  2. 每条 role=assistant 且带 tool_calls 的消息，每个 tool_call 必须有非空 id
 *  3. MOCK_REASONING=1（模拟 thinking 模式）：做过 tool call 的 assistant 轮，
 *     reasoning_content 必须原样回灌
 */
function validateDeepSeekContract(messages) {
  const reasoningRequired = process.env.MOCK_REASONING === '1';
  for (let i = 0; i < messages.length; i++) {
    const m = messages[i];
    if (m.role === 'tool') {
      if (!m.tool_call_id) {
        return `Failed to deserialize the JSON body into the target type: messages[${i}]: missing field \`tool_call_id\``;
      }
    }
    if (m.role === 'assistant' && Array.isArray(m.tool_calls) && m.tool_calls.length > 0) {
      for (let j = 0; j < m.tool_calls.length; j++) {
        if (!m.tool_calls[j]?.id) {
          return `messages[${i}].tool_calls[${j}]: missing field \`id\``;
        }
      }
      if (reasoningRequired && !m.reasoning_content) {
        return 'The `reasoning_content` in the thinking mode must be passed back to the API.';
      }
    }
  }
  return null;
}

function pickIntent(text) {
  const t = (text || '').toLowerCase();
  // 并行工具测试：一轮里发两个 tool_call，验证 ToolLoopExecutor 的并发执行
  if (/(并行|parallel)/.test(t)) return 'parallel';
  if (/(时间|几点|几号|time|date|clock)/.test(t)) return 'time';
  // Memory 系统三件套
  if (/(记住|记下来|remember|帮我记)/.test(t)) return 'record_experience';
  if (/(我说过.*吗|我喜欢什么|记得.*我|recall)/.test(t)) return 'recall_experience';
  if (/(搜索记忆|search.*memory|查记忆)/.test(t)) return 'search_memory';
  // Splatoon3 打工：专用工具优先级高于 web_search / http_fetch
  if (/(打工|salmon\s*run|splatoon.*武器|splatoon.*地图|鲑鱼跑|喷喷.*打工)/.test(t)) return 'splatoon3_salmon';
  // shell 优先级要高于 list_dir / file_read，避免 "跑 ls /" 被误判
  if (/(跑命令|跑\s|shell|exec\s)/.test(t)) return 'shell';
  // SKILL 路由：用户说「分析日志 / 日志」时引导 LLM 走 log-triage skill
  if (/(分析日志|日志诊断|日志.*异常|log.*triage)/.test(t)) return 'skill_log_triage';
  if (/(搜.*内容|grep|包含.*文件|找.*提到|哪些.*文件.*有)/.test(t)) return 'grep';
  if (/(写.*文件|保存.*到|create\s+file|save\s+to|写一个.*文件)/.test(t)) return 'file_write';
  if (/(搜索|search|查一下.*最新|google|百度|web)/.test(t)) return 'web_search';
  if (/(读.*文件|看一下.*文件|cat\s+\/|read\s+file|\/[a-z\-]+\.(txt|md|json|yml))/.test(t)) return 'file_read';
  if (/(列.*目录|看.*下有什么|list.*dir)/.test(t)) return 'list_dir';
  if (/(抓|fetch|og:title|example\.com|网页|standard\sof\s)/.test(t) || /example/.test(t)) return 'fetch';
  if (/(ls\s+\/|^ls$)/.test(t)) return 'shell';
  if (/(插件|plugin)/.test(t)) return 'plugin';
  return 'chat';
}

// 文件工具改为「每用户目录」模型后，路径既可能是绝对路径（落在用户目录内），
// 也可能是相对路径（相对用户目录）。绝对路径优先 —— 入站附件的提示文本里同时有
// 文件名和落盘绝对路径，必须取后者，否则 file_read 找不到文件。
function extractPath(text, fallback = 'bb-demo.txt') {
  const t = text || '';
  const abs = t.match(/\/[\w./\-]+/);
  if (abs) return abs[0];
  const rel = t.match(/[A-Za-z0-9_.\-]+\.[a-z0-9]+/);
  return rel ? rel[0] : fallback;
}

/* ---------------- Handler ---------------- */

async function handleCompletion(req, res, body) {
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ error: 'invalid_json' }));
  }
  const { model = 'mock-gpt-4', messages = [], stream = false, tools = [], stream_options = null } = parsed;
  const id = makeId();
  const sys = systemText(messages);

  if (!stream) {
    // 非流式分支：分类器 / 视觉桥接 / 内部总结都走 chat()（阻塞），在这里应答。
    const uText = lastUserMessage(messages);
    let content;
    if (/任务分类器|回\s*SIMPLE/.test(sys)) {
      // 廉价模型分类：简单问候 / 短文本 → SIMPLE，否则 COMPLEX
      const simple = /(你好|您好|hi|hello|早|嗨|谢谢|在吗|几点)/i.test(uText) || uText.length <= 8;
      content = simple ? 'SIMPLE' : 'COMPLEX';
      console.log(`[mock] classify model=${model} → ${content} ("${uText}")`);
    } else if (/详细描述这张图片|图片描述/.test(sys)) {
      // 视觉桥接：返回一段含方位的文字描述
      content = '画面正中是一只橘色的猫，坐在左下角的木质桌面上；右上角有一扇窗，窗外是蓝天。整体暖色调。';
      console.log(`[mock] vision describe model=${model} hasImage=${messagesHaveImage(messages)}`);
    } else {
      content = '（mock non-stream 回复）';
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      id, object: 'chat.completion', created: Math.floor(Date.now() / 1000), model,
      choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
      usage: buildUsage(messages, content),
    }));
  }

  // 按真实 DeepSeek V4 的契约校验回灌的消息体。任一项不满足都像真实 API 一样回 400，
  // 让本地测试能完整复现生产报错，避免「改一次部署一次」。
  const contractErr = validateDeepSeekContract(messages);
  if (contractErr) {
    console.log(`[mock] 契约校验失败: ${contractErr}`);
    res.writeHead(400, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      error: { message: contractErr, type: 'invalid_request_error', param: null, code: 'invalid_request_error' },
    }));
  }

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
  });

  const userText = lastUserMessage(messages);
  const intent = pickIntent(userText);
  const toolNames = new Set(tools.map(t => t?.function?.name).filter(Boolean));
  const toolFinished = hasToolMessage(messages);

  // 工具阶段未完成时：决定是否要触发 tool call
  const triggerTool = !toolFinished && (
    (intent === 'parallel' && toolNames.has('server_time') && toolNames.has('list_dir'))
    || (intent === 'time' && toolNames.has('server_time'))
    || (intent === 'fetch' && toolNames.has('http_fetch'))
    || (intent === 'shell' && toolNames.has('shell_exec'))
    || (intent === 'file_read' && toolNames.has('file_read'))
    || (intent === 'list_dir' && toolNames.has('list_dir'))
    || (intent === 'file_write' && toolNames.has('file_write'))
    || (intent === 'web_search' && toolNames.has('web_search'))
    || (intent === 'grep' && toolNames.has('grep_search'))
    || (intent === 'skill_log_triage' && toolNames.has('load_skill'))
    || (intent === 'splatoon3_salmon' && toolNames.has('splatoon3_salmon_run'))
    || (intent === 'record_experience' && toolNames.has('record_experience'))
    || (intent === 'recall_experience' && toolNames.has('recall_experience'))
    || (intent === 'search_memory' && toolNames.has('search_memory'))
  );

  console.log(`[mock] intent=${intent} triggerTool=${triggerTool} toolFinished=${toolFinished} userText="${userText}"`);

  // S5 steering 测试：对含 STEERTEST 的首轮（工具未完成）人为延迟，
  // 给测试客户端的第二条消息留出"在 agent 运行期间到达"的窗口。
  if (/STEERTEST/.test(userText) && !toolFinished) {
    await delay(1500);
  }

  if (triggerTool) {
    // thinking 模式：先吐一段 reasoning_content（思维链）
    if (process.env.MOCK_REASONING === '1') {
      for (const seg of ['用户要调工具，', '我先想一下该用哪个…']) {
        sseChunk(res, reasoningChunk(id, model, seg));
        await delay(15);
      }
    }
    // 流出几个 leading 文本 token，然后 tool_calls，然后 finish
    const lead = '让我用工具查一下…';
    for (const ch of lead) {
      sseChunk(res, textChunk(id, model, ch));
      await delay(15);
    }
    if (intent === 'parallel') {
      // 一轮发两个无依赖的 tool_call，index 0 / 1，触发 ToolLoopExecutor 并发执行
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'server_time', '{}', 0));
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'list_dir', JSON.stringify({ path: '' }), 1));
    } else if (intent === 'time') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'server_time', '{}'));
    } else if (intent === 'fetch') {
      // 从用户文本里粗暴提个 URL
      const m = userText.match(/https?:\/\/\S+/);
      const url = m ? m[0] : 'https://example.com';
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'http_fetch', JSON.stringify({ url })));
    } else if (intent === 'shell') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'shell_exec', JSON.stringify({ command: 'ls /' })));
    } else if (intent === 'file_read') {
      const path = extractPath(userText, 'bb-demo.txt');
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'file_read', JSON.stringify({ path })));
    } else if (intent === 'list_dir') {
      // 相对用户目录；留空 = 列用户目录根
      const path = extractPath(userText, '');
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'list_dir', JSON.stringify({ path })));
    } else if (intent === 'file_write') {
      const path = extractPath(userText, 'agent-out.txt');
      // 从用户文本里粗暴取一段 "把 'xxx' 写到" 的内容
      const m = userText.match(/['"]([^'"]+)['"]/);
      const content = m ? m[1] : '由 agent 写入：' + new Date().toISOString();
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'file_write', JSON.stringify({ path, content })));
    } else if (intent === 'web_search') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'web_search', JSON.stringify({ query: userText })));
    } else if (intent === 'grep') {
      // 从文本里粗暴提个关键词
      const tokens = userText.split(/\s+/).filter(s => s.length > 1 && !/^(agent|帮|我|找|搜|grep|的|文件|包含|哪些|提到)$/.test(s));
      const pattern = tokens.length > 0 ? tokens[tokens.length - 1] : 'TODO';
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'grep_search',
          JSON.stringify({ pattern, path: '' })));
    } else if (intent === 'skill_log_triage') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'load_skill', JSON.stringify({ name: 'log-triage' })));
    } else if (intent === 'splatoon3_salmon') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'splatoon3_salmon_run', JSON.stringify({ limit: 2 })));
    } else if (intent === 'record_experience') {
      // 从消息里粗暴取「记住 X」或「remember X」后面的部分作为内容
      const m1 = userText.match(/(?:记住|记下来|remember)[，,:：\s]*([\s\S]+)$/);
      const content = (m1 ? m1[1] : userText).trim().slice(0, 500);
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'record_experience',
          JSON.stringify({ category: 'preferences', content })));
    } else if (intent === 'recall_experience') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'recall_experience',
          JSON.stringify({ category: 'preferences' })));
    } else if (intent === 'search_memory') {
      const tokens = userText.split(/\s+/).filter(s => s.length > 1).slice(-1);
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'search_memory',
          JSON.stringify({ query: tokens[0] || userText.slice(-20) })));
    }
    sseChunk(res, finishChunk(id, model, 'tool_calls'));
    if (stream_options?.include_usage) {
      sseChunk(res, usageChunk(id, model, buildUsage(messages, lead)));
    }
    sseDone(res);
    return;
  }

  // 没工具阶段，或工具结果已经回来，吐 final 文字
  let finalText;
  if (toolFinished) {
    const toolMsg = messages.filter(m => m.role === 'tool').pop();
    const toolResult = typeof toolMsg.content === 'string' ? toolMsg.content : JSON.stringify(toolMsg.content);
    if (intent === 'parallel') {
      const allTools = messages.filter(m => m.role === 'tool')
        .map(m => typeof m.content === 'string' ? m.content : JSON.stringify(m.content));
      finalText = `并行工具结果（共 ${allTools.length} 个）：${allTools.join(' || ').slice(0, 320)}`;
    } else if (intent === 'time') {
      finalText = `根据 server_time 返回：${toolResult}。也就是说现在时间已经拿到了。`;
    } else if (intent === 'fetch') {
      finalText = `已抓取并解析：${toolResult.slice(0, 200)}…`;
    } else if (intent === 'shell') {
      finalText = `沙箱执行结果：${toolResult.slice(0, 200)}`;
    } else if (intent === 'file_read') {
      finalText = `文件内容：${toolResult.slice(0, 200)}`;
    } else if (intent === 'list_dir') {
      finalText = `目录内容：${toolResult.slice(0, 200)}`;
    } else if (intent === 'file_write') {
      finalText = `文件已写入：${toolResult.slice(0, 200)}`;
    } else if (intent === 'web_search') {
      finalText = `搜索结果：${toolResult.slice(0, 200)}`;
    } else if (intent === 'grep') {
      finalText = `搜索匹配：${toolResult.slice(0, 200)}`;
    } else if (intent === 'skill_log_triage') {
      finalText = `已加载 log-triage SKILL，按其指引：${toolResult.slice(0, 200)}`;
    } else if (intent === 'splatoon3_salmon') {
      finalText = `Splatoon3 打工：${toolResult.slice(0, 280)}`;
    } else if (intent === 'record_experience') {
      finalText = `已记入长期记忆：${toolResult.slice(0, 200)}`;
    } else if (intent === 'recall_experience') {
      finalText = `调出长期记忆：${toolResult.slice(0, 280)}`;
    } else if (intent === 'search_memory') {
      finalText = `记忆检索结果：${toolResult.slice(0, 280)}`;
    } else {
      finalText = `工具结果：${toolResult.slice(0, 120)}`;
    }
  } else {
    if (intent === 'plugin') {
      finalText = '我看一下插件信息——目前没有可调用的插件工具，但流式回复链路是通的。';
    } else {
      finalText = '你好，这是一段模拟的流式回复用来验证 bb 协议在 chunked-send 下的分段呈现。第一句结束。第二句继续。第三句收尾，over。';
    }
  }

  for (const ch of finalText) {
    sseChunk(res, textChunk(id, model, ch));
    await delay(8);
  }
  sseChunk(res, finishChunk(id, model, 'stop'));
  if (stream_options?.include_usage) {
    sseChunk(res, usageChunk(id, model, buildUsage(messages, finalText)));
  }
  sseDone(res);
}

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url.startsWith('/v1/chat/completions')) {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => handleCompletion(req, res, body));
    return;
  }
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    return res.end('ok\n');
  }
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('not found\n');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock-openai] listening on http://127.0.0.1:${PORT}`);
  console.log('[mock-openai] POST /v1/chat/completions (SSE stream) + /health');
});
