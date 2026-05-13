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

const argv = Object.fromEntries(process.argv.slice(2).map(a => {
  const [k, v] = a.replace(/^--/, '').split('=');
  return [k, v === undefined ? true : v];
}));

/* ---------------- WebSocket helper ---------------- */

class BbClient {
  constructor({ url = WS_URL, appId = APP_ID, secret = SECRET, userId = 'tester-001' } = {}) {
    this.url = url;
    this.appId = appId;
    this.secret = secret;
    this.userId = userId;
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
        // 首帧：认证
        this.ws.send(JSON.stringify({ appId: this.appId, secret: this.secret }));
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
