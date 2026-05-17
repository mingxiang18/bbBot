# bbBot 本地测试环境（scripts/dev/）

零外部依赖（除复用 misu-server 的 MySQL）地把 M1-M7 全套 agent 能力跑起来，用 bb 私有协议测。

## TL;DR

```bash
# 前提：misu-server 那边的 MySQL 容器 misu-mysql-local 已在 3316 端口上跑
docker ps --filter name=misu-mysql-local

# 启动 + 跑全部场景
./scripts/dev/bb-dev.sh up
./scripts/dev/bb-dev.sh test
./scripts/dev/bb-dev.sh down
```

跑通的样子：

```
=== A1: A1 流式聊天 (BB 协议下 chunked-send 验证) ===
  ← [0] type=private text="你好，这是一段模拟的流式回复用来验证 bb 协议在 chunked-send 下的分段呈现。…"
  ← [1] type=private text="第三句收尾，over。"
  → ✅ PASS (6537ms)
=== A2: A2 工具调用 (http_fetch) ===   → ✅ PASS
=== A3: A3 工具调用 (server_time) ===  → ✅ PASS
=== A4: A4 授权拒绝 (非 owner 调 shell_exec) ===  → ✅ PASS
=== A5: A5 角色管理 (owner 用 /aiAgent.role.grant) ===  → ✅ PASS
=== A9: A9 回归 (无 agent 前缀 → 走聊天人格) ===  → ✅ PASS
=== 汇总：6/6 通过 ===
```

## 为什么这样搭

- **bb 私有协议**最适合做端到端验收 —— 不依赖真实 QQ / Telegram / Discord 账号，但流式（chunked-send 路径）、工具循环、授权、审计的链路与生产完全相同。
- **复用 misu-server 的 MySQL**（端口 3316，容器 `misu-mysql-local`）—— 不另起 docker compose，对开发机零负担。
- **mock OpenAI SSE 服务**（`mock-openai-server.mjs`）—— 模拟 OpenAI `/v1/chat/completions` 的流式 + function calling 协议，按用户文本路由：「时间」→ server_time 工具，「抓 / fetch」→ http_fetch，「跑命令 / ls」→ shell_exec，其余 → 三句流式纯文本。
- **Node WS 测试客户端**（`bb-client.mjs`）—— 完成 BB 认证握手，逐条发 `BbReceiveMessage`，收 `BbSocketServerMessage`，自动断言。

## 端口表

| 端口 | 角色 |
|---|---|
| 3316 | 复用 misu-mysql-local（root/root） |
| 18199 | bbBot HTTP（避开默认 8199 / 8080 等） |
| 18765 | bbBot BB 私有协议 WebSocket |
| 18800 | mock OpenAI SSE |

数据库：`bb_bot_local`（init SQL 自动建）。Spring profile：`bbtest`（`application-bbtest.yml` 覆盖 `application-local.yml` 的关键字段）。

## 目录结构

```
scripts/dev/
├── README.md                   # 本文件
├── bb-dev.sh                   # orchestrator（up/down/build/test/repl/logs/status）
├── sql/01-init-bb-bot-local.sql  # bb_bot_local 库 + 基础 4 表（dispatcher / chat / clue / 占位）
├── mock-openai-server.mjs      # mock OpenAI /v1/chat/completions SSE
├── bb-client.mjs               # BB 协议测试客户端 + A1..A9 场景
└── run/                        # 运行时产物（gitignored）
    ├── pids/{bot,mock}.pid
    └── logs/{bot,mock}.log
```

profile 配置在仓库内（提交版本控制）：`bb-bot-server/src/main/resources/application-bbtest.yml`。

## 常用命令

```bash
./scripts/dev/bb-dev.sh up [--build]    # 启动；--build 强制重建 jar
./scripts/dev/bb-dev.sh down            # 停 bot + mock
./scripts/dev/bb-dev.sh status          # 查运行状态
./scripts/dev/bb-dev.sh logs bot        # tail bbBot 日志
./scripts/dev/bb-dev.sh logs mock       # tail mock 日志
./scripts/dev/bb-dev.sh build [--force] # 仅 build jar
./scripts/dev/bb-dev.sh init-db         # 仅初始化数据库
./scripts/dev/bb-dev.sh test            # 跑全部场景
./scripts/dev/bb-dev.sh test A2         # 只跑 A2
./scripts/dev/bb-dev.sh repl            # 进交互式 REPL，手动收发
```

## 验收场景说明

| ID | 测什么 | 期望 |
|---|---|---|
| A1 | 流式聊天 → BbAiChatHandler → AiChatClient.askChatGPTStream → BbToBbMessageApi 的 BbStreamSession（chunked-send 按句号切段） | 收到 ≥ 2 帧，能看到三句被分段送达 |
| A2 | `agent 抓一下 https://example.com 的标题` → BbAiAgentHandler → function calling 循环 → http_fetch 工具实际去抓 example.com → 结果回灌 LLM → 流式吐回 | 回复包含 example.com 的真实抓取内容 |
| A3 | `agent 现在几点` → server_time 工具 → 结果回灌 → 流式吐时间 | 回复包含 iso / zone / dayOfWeek |
| A4 | stranger-999 发 `agent 跑一下 ls /` → 进入 shell_exec 调用 → AiAgentAuthService 命中 `requires_owner=true` → 拒绝 → tool 结果是 `permission_denied`，LLM 据此回复 | 收到包含 `permission_denied / requires_owner / 无权限` 的文本，且 `ai_tool_invocation_log` 落一行 `status=denied` |
| A5 | owner 发 `/aiAgent.role.grant guest-777 admin` → BbAiAgentAdminHandler → `ai_user_role` 落库 | "已授予" 回复，DB 表里有新行 |
| A9 | 普通问候 `你好` → 走默认聊天分支（BbAiChatHandler） | 至少 1 帧回复 |

A6 沙箱、A7 cron、A8 插件在 BB 协议测试客户端里没覆盖：

- **A6**：mock 模式下 SandboxRunnerFactory 选 `noop`（Mac 没 bwrap），shell_exec 调用会返回 `sandbox_unavailable`，A4 实际就走过了这条降级路径。生产 Linux + bwrap 才能验真正的沙箱挂载效果。
- **A7**：cron 至少需要等一个分钟整点触发，自动化测试不实用。`./scripts/dev/bb-dev.sh repl` 进交互模式手动 `/aiAgent.cron.add "0 */1 * * * *" 报时` 看下一分钟的自动执行更直观。
- **A8**：需要一个示例 plugin jar，超出本测试环境范围。

## REPL 模式

需要手动玩的场景：

```
$ ./scripts/dev/bb-dev.sh repl
> agent 现在几点
  ← [*] type=private text="让我用工具查一下…根据 server_time 返回：{...}"
> /aiAgent.audit guest-777 7
  ← [*] type=private text="guest-777 近 7 天工具调用..."
> :q
```

默认用 `tester-owner` 身份登录。换身份：`BB_APP_ID=... BB_SECRET=... node scripts/dev/bb-client.mjs --interactive`。

## 数据库随时清空

```bash
docker exec -i misu-mysql-local mysql -uroot -proot \
  -e "DROP DATABASE IF EXISTS bb_bot_local;"
./scripts/dev/bb-dev.sh init-db
./scripts/dev/bb-dev.sh down && ./scripts/dev/bb-dev.sh up
```

## 切换到真实 OpenAI

把 `application-bbtest.yml` 里 `chatGPT.url` 改成真实 endpoint，`chatGPT.apiKey` 填真 key 即可。mock-openai 进程不启也没关系（`bb-dev.sh up` 启动它没成本，bbBot 不会去连）。

## 常见问题

- **`misu-mysql-local 没在跑`** → 到 `~/IdeaProjects/misu-server` 那边 `./dev.sh up` 把容器起来。
- **`bb_bot_local 表里有数据，bbBot 报 Unknown column`** → DB schema 漂移了，按上一节"随时清空"重建即可。
- **`bbBot 60s 内未就绪`** → 看 `scripts/dev/run/logs/bot.log`，常见原因是 MySQL 拒连或端口 18199/18765 被占。
- **测试客户端 connect 失败** → 确认 `bb-dev.sh status` 里 `bot` 是 running，否则 mock + bbBot 都要重启。
- **更改源码后** → `./scripts/dev/bb-dev.sh down && ./scripts/dev/bb-dev.sh up --build`。
