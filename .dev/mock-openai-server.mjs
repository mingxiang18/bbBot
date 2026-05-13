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
function toolCallChunk(id, model, callId, fnName, argsJson) {
  return {
    id, object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model,
    choices: [{
      index: 0,
      delta: {
        tool_calls: [
          {
            index: 0, id: callId, type: 'function',
            function: { name: fnName, arguments: argsJson },
          },
        ],
      },
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

function pickIntent(text) {
  const t = (text || '').toLowerCase();
  if (/(时间|几点|几号|time|date|clock)/.test(t)) return 'time';
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

function extractPath(text) {
  const m = (text || '').match(/(\/[\w./\-]+)/);
  return m ? m[1] : '/tmp/bb-demo.txt';
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
  const { model = 'mock-gpt-4', messages = [], stream = false, tools = [] } = parsed;
  const id = makeId();

  if (!stream) {
    // 非流式分支（本测试 profile 用不到，但兜底实现）
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      id, object: 'chat.completion', created: Math.floor(Date.now() / 1000), model,
      choices: [{ index: 0, message: { role: 'assistant', content: '（mock non-stream 回复）' }, finish_reason: 'stop' }],
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
    (intent === 'time' && toolNames.has('server_time'))
    || (intent === 'fetch' && toolNames.has('http_fetch'))
    || (intent === 'shell' && toolNames.has('shell_exec'))
    || (intent === 'file_read' && toolNames.has('file_read'))
    || (intent === 'list_dir' && toolNames.has('list_dir'))
    || (intent === 'file_write' && toolNames.has('file_write'))
    || (intent === 'web_search' && toolNames.has('web_search'))
    || (intent === 'grep' && toolNames.has('grep_search'))
    || (intent === 'skill_log_triage' && toolNames.has('load_skill'))
    || (intent === 'splatoon3_salmon' && toolNames.has('splatoon3_salmon_run'))
  );

  console.log(`[mock] intent=${intent} triggerTool=${triggerTool} toolFinished=${toolFinished} userText="${userText}"`);

  if (triggerTool) {
    // 流出几个 leading 文本 token，然后 tool_calls，然后 finish
    const lead = '让我用工具查一下…';
    for (const ch of lead) {
      sseChunk(res, textChunk(id, model, ch));
      await delay(15);
    }
    if (intent === 'time') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'server_time', '{}'));
    } else if (intent === 'fetch') {
      // 从用户文本里粗暴提个 URL
      const m = userText.match(/https?:\/\/\S+/);
      const url = m ? m[0] : 'https://example.com';
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'http_fetch', JSON.stringify({ url })));
    } else if (intent === 'shell') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'shell_exec', JSON.stringify({ command: 'ls /' })));
    } else if (intent === 'file_read') {
      const path = extractPath(userText);
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'file_read', JSON.stringify({ path })));
    } else if (intent === 'list_dir') {
      const path = extractPath(userText);
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'list_dir', JSON.stringify({ path })));
    } else if (intent === 'file_write') {
      const path = extractPath(userText) || '/tmp/bb-bot-test/agent-out.txt';
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
          JSON.stringify({ pattern, path: '/tmp/bb-bot-test' })));
    } else if (intent === 'skill_log_triage') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'load_skill', JSON.stringify({ name: 'log-triage' })));
    } else if (intent === 'splatoon3_salmon') {
      sseChunk(res, toolCallChunk(id, model, makeToolId(), 'splatoon3_salmon_run', JSON.stringify({ limit: 2 })));
    }
    sseChunk(res, finishChunk(id, model, 'tool_calls'));
    sseDone(res);
    return;
  }

  // 没工具阶段，或工具结果已经回来，吐 final 文字
  let finalText;
  if (toolFinished) {
    const toolMsg = messages.filter(m => m.role === 'tool').pop();
    const toolResult = typeof toolMsg.content === 'string' ? toolMsg.content : JSON.stringify(toolMsg.content);
    if (intent === 'time') {
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
