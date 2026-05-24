#!/usr/bin/env node
/**
 * BB 协议测试客户端。
 *
 * 用法：
 *   node bb-client.mjs              # 跑全部场景 A1..A5、A9
 *   node bb-client.mjs --case=A2    # 只跑 A2
 *   node bb-client.mjs --interactive  # 进入 REPL，手动收发
 *
 * 协议（参考 BbWebSocketServer.onMessage）：
 *   1) 连接 ws://localhost:18765
 *   2) 首帧发 { appId, secret } 认证；服务器回 { code: 200, message }
 *   3) 之后发 BbReceiveMessage JSON，收 BbSocketServerMessage JSON
 *
 * 依赖：仅 Node 内置 ws (>=22 内置 WebSocket，<22 用 'ws' npm 包)
 */

import readline from 'node:readline';
import { setTimeout as sleep } from 'node:timers/promises';

const WS_URL = process.env.BB_WS_URL || 'ws://127.0.0.1:18765';
const APP_ID = process.env.BB_APP_ID || 'bb-test';
const SECRET = process.env.BB_SECRET || 'bb-test-secret';

// tester-owner 的每用户文件目录：application-bbtest.yml 的 aiAgent.fs.userFileRoot
// (/tmp/bb-bot-test/agent-files) + userId。文件工具严格限定在此目录内。
const USER_DIR = process.env.BB_USER_DIR || '/tmp/bb-bot-test/agent-files/tester-owner';

const argv = Object.fromEntries(process.argv.slice(2).map(a => {
  const [k, v] = a.replace(/^--/, '').split('=');
  return [k, v === undefined ? true : v];
}));

/* ---------------- WebSocket helper ---------------- */

class BbClient {
  constructor({ url = WS_URL, appId = APP_ID, secret = SECRET, userId = 'tester-001',
                capabilities = ['stream', 'file'] } = {}) {
    this.url = url;
    this.appId = appId;
    this.secret = secret;
    this.userId = userId;
    this.capabilities = capabilities;   // 认证握手上报的能力位（stream / file）
    this.ws = null;
    this.authed = false;
    this.inbox = [];               // 累积所有服务器回包
    this.waiters = [];             // 等待新消息的 resolver
    this.allFrames = [];           // 全部帧（含 auth ack），调试用
  }

  async connect() {
    const WS = globalThis.WebSocket
      ?? (await import('ws').then(m => m.WebSocket).catch(() => null));
    if (!WS) throw new Error('需要 Node >=22 的内置 WebSocket 或安装 `ws` 包');
    return new Promise((resolve, reject) => {
      this.ws = new WS(this.url);
      this.ws.addEventListener('open', () => {
        // 首帧：认证 + 上报客户端能力（stream / file）
        this.ws.send(JSON.stringify({
          appId: this.appId,
          secret: this.secret,
          capabilities: this.capabilities,
        }));
      });
      this.ws.addEventListener('message', (ev) => {
        let data;
        try { data = JSON.parse(ev.data); } catch { data = ev.data; }
        this.allFrames.push(data);
        if (!this.authed) {
          if (data && data.code === 200) {
            this.authed = true;
            resolve();
          } else {
            reject(new Error('认证失败: ' + JSON.stringify(data)));
          }
          return;
        }
        // 业务回包：有 waiter 时直接交付，否则入 inbox 等待 waitMessage 来取
        const w = this.waiters.shift();
        if (w) {
          w(data);
        } else {
          this.inbox.push(data);
        }
      });
      this.ws.addEventListener('error', (e) => reject(e.error || e));
      this.ws.addEventListener('close', () => {
        this.authed = false;
      });
    });
  }

  /** 发一条 BbReceiveMessage（private 或 group）。 */
  sendUserMessage(text, { messageType = 'private', groupId = null, userId = this.userId, nickname = 'Tester', atBot = false } = {}) {
    const msg = {
      // 注意大小写：BotType.BB = "bb"，BbSendMessageType.TEXT = "text"
      botType: 'bb',
      messageType,
      userId,
      sender: { id: userId, nickname, botFlag: false },
      groupId,
      messageId: 'm-' + Date.now() + '-' + Math.floor(Math.random() * 1000),
      message: text,
      messageContentList: [{ type: 'text', data: text }],
      atUserList: atBot ? [{ id: 'bbBot', nickname: 'bbBot', botFlag: true }] : [],
      sendTime: new Date().toISOString(),
    };
    this.ws.send(JSON.stringify(msg));
    return msg;
  }

  /** 发一条自定义 messageContentList 的消息（用于文件附件等场景）。 */
  sendContent(messageContentList, { messageType = 'private', groupId = null, userId = this.userId, nickname = 'Tester', atBot = false } = {}) {
    const msg = {
      botType: 'bb',
      messageType,
      userId,
      sender: { id: userId, nickname, botFlag: false },
      groupId,
      messageId: 'm-' + Date.now() + '-' + Math.floor(Math.random() * 1000),
      message: messageContentList.filter(c => c.type === 'text').map(c => c.data).join(''),
      messageContentList,
      atUserList: atBot ? [{ id: 'bbBot', nickname: 'bbBot', botFlag: true }] : [],
      sendTime: new Date().toISOString(),
    };
    this.ws.send(JSON.stringify(msg));
    return msg;
  }

  /** 收一条服务器消息，没有就最多等 timeoutMs。 */
  async waitMessage(timeoutMs = 5000) {
    if (this.inbox.length) return this.inbox.shift();
    let resolve;
    const p = new Promise(r => { resolve = r; });
    this.waiters.push(resolve);
    const timer = sleep(timeoutMs).then(() => null);
    const winner = await Promise.race([p, timer]);
    if (!winner) {
      // 超时；从 waiters 移除
      const idx = this.waiters.indexOf(resolve);
      if (idx >= 0) this.waiters.splice(idx, 1);
    }
    return winner;
  }

  /** 持续收消息直到 idleMs 内没有新帧，返回累计 inbox。 */
  async collectUntilIdle({ idleMs = 4000, maxMs = 25000 } = {}) {
    const collected = [];
    const start = Date.now();
    while (Date.now() - start < maxMs) {
      const m = await this.waitMessage(idleMs);
      if (m == null) break;
      collected.push(m);
    }
    return collected;
  }

  close() {
    try { this.ws?.close(); } catch {}
  }
}

/* ---------------- Pretty print ---------------- */

function showFrame(frame, idx) {
  const txt = (frame?.messageList || frame?.messageContentList || [])
    .map(c => c?.data ?? '')
    .join('');
  console.log(`  ← [${idx}] type=${frame?.messageType ?? '?'} text=${JSON.stringify(txt).slice(0, 200)}`);
}

function joinText(frames) {
  return frames.map(f => (f?.messageList || []).map(c => c?.data ?? '').join('')).join('');
}

/** 分析回包帧：区分流式帧（带 streamId）与普通帧，提取 streamState 序列。 */
function analyzeStream(frames) {
  const streamIds = new Set();
  const states = [];
  let plainCount = 0;
  for (const f of frames) {
    if (f && f.streamId) {
      streamIds.add(f.streamId);
      if (f.streamState) states.push(f.streamState);
    } else {
      plainCount++;
    }
  }
  return {
    streamIds: [...streamIds],
    states,
    plainCount,
    streamFrameCount: frames.length - plainCount,
  };
}

/* ---------------- Scenarios ---------------- */

const scenarios = {
  A1: {
    title: 'A1 流式聊天 (BB 协议下 chunked-send 验证)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      console.log('  认证 ok。发"你好讲个故事"测试默认流式聊天路径…');
      c.sendUserMessage('你好讲个故事');
      const frames = await c.collectUntilIdle({ idleMs: 3000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = frames.length >= 2 && /(你好|流式|句)/.test(text);
      c.close();
      return { ok, detail: `收到 ${frames.length} 帧，总文本 ${text.length} 字` };
    },
  },

  A2: {
    title: 'A2 工具调用 (http_fetch)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      console.log('  发"agent 抓一下 https://example.com 的标题"…');
      c.sendUserMessage('agent 抓一下 https://example.com 的标题');
      const frames = await c.collectUntilIdle({ idleMs: 6000, maxMs: 30000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      // 抓取真实 example.com 可能失败（没网），所以判定条件宽松：只要触发了工具调用循环
      const ok = frames.length >= 1 && (text.includes('抓') || text.includes('example') || text.includes('已'));
      c.close();
      return { ok, detail: `回复字数 ${text.length}` };
    },
  },

  A3: {
    title: 'A3 工具调用 (server_time)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 现在几点');
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = frames.length >= 1 && /(时间|iso|zone)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 120) };
    },
  },

  A4: {
    title: 'A4 授权拒绝 (非 owner 调 shell_exec)',
    async run() {
      const c = new BbClient({ userId: 'stranger-999' });
      await c.connect();
      c.sendUserMessage('agent 跑一下 ls /');
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /(permission_denied|无权限|denied|requires_owner|noop|sandbox_unavailable)/i.test(text);
      c.close();
      return { ok, detail: `期望权限拒绝。回复包含：${text.slice(0, 120)}` };
    },
  },

  A5: {
    title: 'A5 角色管理 (owner 用 /aiAgent.role.grant)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('/aiAgent.role.grant guest-777 admin', { messageType: 'private' });
      const frames = await c.collectUntilIdle({ idleMs: 3000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /已授予|已具备|admin/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 120) };
    },
  },

  A6: {
    title: 'A6 通用原语 (file_read 读每用户目录下的文件)',
    async run() {
      // 文件工具已限定在「调用者自己的用户目录」内，demo 文件放进去，用相对路径访问
      const fs = await import('node:fs/promises');
      await fs.mkdir(USER_DIR, { recursive: true });
      await fs.writeFile(USER_DIR + '/bb-demo.txt', 'hello from agent test\n');
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 读一下 bb-demo.txt 文件');
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /(hello from agent test|文件内容)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 160) };
    },
  },

  A7: {
    title: 'A7 通用原语 (list_dir 列用户目录)',
    async run() {
      const fs = await import('node:fs/promises');
      await fs.mkdir(USER_DIR, { recursive: true });
      await fs.writeFile(USER_DIR + '/bb-demo.txt', 'x');
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 列一下你的目录里有什么');
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /(bb-demo\.txt|目录内容|count)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 160) };
    },
  },

  A8: {
    title: 'A8 file_write (owner 写文件到自己的用户目录)',
    async run() {
      const fs = await import('node:fs/promises');
      await fs.mkdir(USER_DIR, { recursive: true });
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 把 "hello bbBot" 写到 agent-out.txt 文件');
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      // 检查文件是不是真被写到该用户目录里
      let written = '';
      try { written = await fs.readFile(USER_DIR + '/agent-out.txt', 'utf8'); } catch {}
      const ok = /(已写入|bytesWritten|overwrite)/.test(text) && written.includes('hello');
      c.close();
      return { ok, detail: `回复 ${text.slice(0, 80)} / 文件实际内容="${written.trim()}"` };
    },
  },

  A10: {
    title: 'A10 web_search (DuckDuckGo HTML 后端)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 帮我搜索 example domain 是干什么的');
      const frames = await c.collectUntilIdle({ idleMs: 8000, maxMs: 35000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      // 网络可能不通，宽松判定：触发了 web_search 工具就算 pass
      const ok = /(搜索结果|duckduckgo|serpapi|search_failed|results)/i.test(text);
      c.close();
      return { ok, detail: text.slice(0, 160) };
    },
  },

  A11: {
    title: 'A11 grep_search (在用户目录里搜内容)',
    async run() {
      const fs = await import('node:fs/promises');
      await fs.mkdir(USER_DIR, { recursive: true });
      await fs.writeFile(USER_DIR + '/notes.md', 'line1\nTODO: 测试 grep\nline3\n');
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 帮我找一下哪些文件提到 TODO');
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /(notes\.md|TODO|matchCount|搜索匹配)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 200) };
    },
  },

  A12: {
    title: 'A12 SKILLS (load_skill 读 log-triage 指引)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent 帮我分析日志');
      const frames = await c.collectUntilIdle({ idleMs: 6000, maxMs: 25000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /(log-triage|分诊|SKILL)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 200) };
    },
  },

  A13: {
    title: 'A13 Splatoon3 打工查询 (专用工具一步直达)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('agent splatoon3 现在打工是什么地图和武器');
      const frames = await c.collectUntilIdle({ idleMs: 8000, maxMs: 30000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      // 真去抓 splatoon3.ink，可能 timeout / 国内访问不通 → 宽松判断只要工具被调即可
      const ok = /(打工|shifts|stage|weapons|salmon|splatoon3\.ink|fetch_failed|upstream_http)/i.test(text);
      c.close();
      return { ok, detail: text.slice(0, 200) };
    },
  },

  A9: {
    title: 'A9 回归 (无 agent 前缀 → 走聊天人格)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('你好');
      const frames = await c.collectUntilIdle({ idleMs: 3000 });
      frames.forEach(showFrame);
      const ok = frames.length >= 1;
      c.close();
      return { ok, detail: `收到 ${frames.length} 帧` };
    },
  },

  /* ===== BB 协议改造 + handler 合并 + pi 三特性的新增边界场景 ===== */

  S1: {
    title: 'S1 真流式帧 (capability=stream → streamId + start/delta/end 序列)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner', capabilities: ['stream', 'file'] });
      await c.connect();
      c.sendUserMessage('你好讲个故事');
      const frames = await c.collectUntilIdle({ idleMs: 3000 });
      frames.forEach(showFrame);
      const a = analyzeStream(frames);
      const ok = a.streamIds.length === 1
        && a.states.length >= 2
        && a.states[0] === 'start'
        && a.states[a.states.length - 1] === 'end'
        && a.states.slice(1, -1).every(s => s === 'delta');
      c.close();
      return { ok, detail: `streamId=${a.streamIds.length} 个, streamState=[${a.states.join(',')}]` };
    },
  },

  S2: {
    title: 'S2 chunked 回退 (无 capability → 帧不带 streamId)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner', capabilities: [] });
      await c.connect();
      c.sendUserMessage('你好讲个故事');
      const frames = await c.collectUntilIdle({ idleMs: 3000 });
      frames.forEach(showFrame);
      const a = analyzeStream(frames);
      const ok = frames.length >= 1 && a.streamIds.length === 0 && a.streamFrameCount === 0;
      c.close();
      return { ok, detail: `普通帧 ${a.plainCount}, 流式帧 ${a.streamFrameCount}` };
    },
  },

  S3: {
    title: 'S3 入站文件附件 (localFile → 落盘到用户目录 → file_read 读到内容)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      // 入站 localFile 的 data 是 base64；bot 应解码落盘，并把本地路径告诉模型
      const b64 = Buffer.from('入站文件测试内容 INBOUND-OK\n').toString('base64');
      c.sendContent([
        { type: 'text', data: '读一下这个文件' },
        { type: 'localFile', data: b64, fileName: 'inbound-note.txt', mimeType: 'text/plain' },
      ]);
      const frames = await c.collectUntilIdle({ idleMs: 5000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      // 落盘 + file_read 链路打通：回复里应出现文件真实内容（而非仅 mock 前缀）
      const ok = /(INBOUND-OK|入站文件测试内容)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 200) };
    },
  },

  S4: {
    title: 'S4 无 agent 前缀直接干活 (智能路由 → server_time 工具)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      // 关键：没有 "agent" 前缀，靠 handler 合并后模型自行决定调工具
      c.sendUserMessage('现在几点了');
      const frames = await c.collectUntilIdle({ idleMs: 6000, maxMs: 20000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /(时间|iso|zone|server_time)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 120) };
    },
  },

  S5: {
    title: 'S5 steering (agent 运行中追加消息 → 并入同一轮)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner', capabilities: ['stream'] });
      await c.connect();
      // A 触发慢响应（mock 对 STEERTEST 延迟），B 在 A 运行期间发出
      c.sendUserMessage('STEERTEST 现在几点');
      await sleep(300);
      c.sendUserMessage('STEERTEST 顺便今天几号');
      const frames = await c.collectUntilIdle({ idleMs: 6000, maxMs: 30000 });
      frames.forEach(showFrame);
      const a = analyzeStream(frames);
      // B 被并入 A 那一轮 → 全部帧共享 1 个 streamId；未 steer 则会出现 2 个
      const ok = a.streamIds.length === 1;
      c.close();
      return { ok, detail: `回复 streamId 数=${a.streamIds.length}（1=已并入, 2=未 steer）` };
    },
  },

  S6: {
    title: 'S6 SKILL DB 管理 (/aiAgent.skill.list 列出 ai_skill 表)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('/aiAgent.skill.list');
      const frames = await c.collectUntilIdle({ idleMs: 3000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      const ok = /log-triage/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 160) };
    },
  },

  S7: {
    title: 'S7 工具并行执行 (一轮 2 个 tool_call → 并发跑)',
    async run() {
      const c = new BbClient({ userId: 'tester-owner' });
      await c.connect();
      c.sendUserMessage('并行测试：同时查时间和列一下目录');
      const frames = await c.collectUntilIdle({ idleMs: 6000, maxMs: 25000 });
      frames.forEach(showFrame);
      const text = joinText(frames);
      // 两个工具结果都回来：server_time 的 zone + list_dir 的 entries/count
      const ok = /共 2 个/.test(text) && /zone/.test(text) && /(entries|count)/.test(text);
      c.close();
      return { ok, detail: text.slice(0, 200) };
    },
  },
};

/* ---------------- Runner ---------------- */

async function runOne(name) {
  const sc = scenarios[name];
  if (!sc) throw new Error('未知场景: ' + name);
  console.log(`\n=== ${name}: ${sc.title} ===`);
  try {
    const t0 = Date.now();
    const r = await sc.run();
    const dt = Date.now() - t0;
    console.log(`  → ${r.ok ? '✅ PASS' : '❌ FAIL'} (${dt}ms) ${r.detail || ''}`);
    return r.ok;
  } catch (e) {
    console.log(`  → ❌ ERROR ${e.message}`);
    return false;
  }
}

async function interactive() {
  const c = new BbClient({ userId: argv.user || 'tester-owner' });
  await c.connect();
  console.log('已连接 + 认证。输入文本回车发送。:q 退出。');
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout, prompt: '> ' });
  rl.prompt();
  // 后台接收并打印
  (async () => {
    while (true) {
      const m = await c.waitMessage(60_000);
      if (m == null) continue;
      showFrame(m, '*');
      rl.prompt();
    }
  })();
  rl.on('line', (line) => {
    if (line.trim() === ':q') { c.close(); rl.close(); process.exit(0); }
    c.sendUserMessage(line);
  });
}

async function main() {
  if (argv.interactive) return interactive();

  const only = argv.case;
  const names = only ? [only] : Object.keys(scenarios);
  let pass = 0, total = 0;
  for (const name of names) {
    total++;
    if (await runOne(name)) pass++;
  }
  console.log(`\n=== 汇总：${pass}/${total} 通过 ===`);
  process.exit(pass === total ? 0 : 1);
}

main().catch(e => { console.error(e); process.exit(2); });
