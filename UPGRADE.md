# bbBot AI Agent 升级部署文档

> 把当前 master 升级到 `claude/gifted-tesla-c73b2e` 分支（M1-M8 全部完成）。
> 23 个 commits、103 文件、+9 200 行 / -130 行，新增完整的 Agent 能力 + 三层长期记忆系统。

---

## 一、本次升级带来了什么

按 milestone 分组（每个都有独立 commit，可逐个 cherry-pick）：

| Milestone | 主要功能 | 关键 commit |
|---|---|---|
| **M1** 流式聊天 | TG / Discord / BB edit-message 真流式；OneBot / QQ 协议不支持改消息，走 fallback（完整回复一次发，不强开 chunked-send） | `6ba8c085` |
| **M2 + M3** 工具调用 + Agent 路由 | `@AiTool` 注解 + 注册表 + function calling 循环 + `agent <prompt>` 路由 | `f28b438d` |
| **M4** MySQL 授权 + 审计 | `ai_user_role` / `ai_tool_policy` / `ai_tool_invocation_log` 三表 + admin 命令 | `ff14e24d` |
| **M5** OS 沙箱 | Bubblewrap / Docker / NoOp 三后端 + ShellExecTool | `f5e3b871` |
| **M6** Cron 系统 | `ai_cron_task` 表 + `@Scheduled` 扫描 + `/aiAgent.cron.*` 命令 | `60a36148` |
| **M7** 插件加载 | `plugins/*.jar` + `plugin.json` + URLClassLoader 隔离 | `5b00b7c8` |
| **通用工具** | `file_read` / `list_dir` / `file_write` / `web_search` / `grep_search` | `7a0253a0` / `688b9c2a` |
| **SKILLS 体系** | `skills/<name>/SKILL.md` 自动注入 system prompt + `load_skill` 工具 | `688b9c2a` |
| **Splatoon3 专用工具** | `splatoon3_salmon_run` 一步直达 schedules.json | `beebdeb1` |
| **M8 记忆系统重构** | `ai_memory_event` + `ai_memory_session` + `ai_memory_fact` 三表 + 4 阶段编译 + 跨进程持久 | `49cfa470` → `175fdf0a` |
| **本地测试环境** | `scripts/dev/bb-dev.sh` + mock OpenAI + BB 协议测试客户端 + 13 个 A 场景 | `93aafe2b` |
| **真实 LLM 接入** | `application-bbtest.yml` 环境变量化 + `real-llm.env` 自动 source + `which-llm` 命令 | `6e997bb9` |

注册到 `AiToolRegistry` 的工具最终为 **13 个** + **1 个示例 SKILL**：

```
http_fetch, server_time, file_read, list_dir, file_write,
web_search, grep_search, shell_exec, load_skill,
splatoon3_salmon_run, search_memory, recall_experience, record_experience
```

---

## 二、前置依赖（生产部署前必须满足）

| 依赖 | 版本要求 | 备注 |
|---|---|---|
| Java | 17+ | bbBot 已用 17 |
| Maven | 3.6+ | 或用 IntelliJ 自带 |
| MySQL | **8.0+**（强制） | M8 用了 `WITH PARSER ngram` 做 CJK 全文索引；5.7 起就有，但 8.0 更稳 |
| Bubblewrap (`bwrap`) | 可选 | Linux 上 shell_exec 沙箱后端；缺失则自动降级 Docker，都缺 NoOp 拒绝执行 |
| Docker | 可选 | shell_exec 沙箱备选 |
| 磁盘 | ≥ 100 MB | 记忆 workspace 目录（每用户约 1-5 MB） |
| OpenAI 兼容 LLM | 任一 | DeepSeek / Moonshot / 通义千问 / OpenAI / Kimi —— 必须支持 function calling |

**最严苛的依赖**：MySQL 必须 8.0+。如果你生产是 5.7，**FactStore 全文搜索会失效**（FALL BACK 到 LIKE 模糊搜，搜中文效果差）。建议先升 MySQL。

---

## 三、配置变更总览（application.yml）

新增 / 修改的配置块全部加在 [application.yml](bb-bot-server/src/main/resources/application.yml)。**所有字段都有合理默认值**，最小化配置就能跑。

### 必须看一眼

```yaml
chatGPT:
  apiKey: sk-你的-key                                  # 必填
  url: https://api.openai.com/v1/chat/completions      # 真实 endpoint
  model: gpt-4o-mini                                   # 也可 deepseek-chat / qwen-plus / moonshot-v1-8k
  # ★ 新增：流式聊天开关，老部署默认关闭兼容
  streamEnabled: true                                  # 推荐开启
```

### Agent 必填

```yaml
aiAgent:
  owners: "12345678,87654321"        # 必填：你的 owner user id（QQ/TG 等），逗号分多个
  maxSteps: 10                       # function calling 循环上限
  autoCreateTables: true             # 启动自动建表（推荐 true）
```

### Agent 可选

```yaml
aiAgent:
  sandbox:
    preferred: auto                  # auto / bubblewrap / docker / noop
    dockerImage: alpine:3.20
  pluginDir: ./plugins
  skillsDir: ./skills
  fs:
    allowedRoots: /tmp               # file_read / list_dir / grep_search 白名单
    writeRoots: /tmp/bb-bot-data     # file_write 白名单（更严）
  webSearch:
    serpApiKey: ""                   # 空则用 DuckDuckGo HTML（无需 key）
  memory:                            # M8 新增
    workspaceDir: ./memory-workspace
    sessionGapMinutes: 30
    factsSummaryWindow: 30
    weekDays: 7
    longtermDays: 365
    factMaxBytes: 64000
    memoryMdMaxChars: 6000
```

### 完整示例

参考 [`bb-bot-server/src/main/resources/application-bbtest.yml`](bb-bot-server/src/main/resources/application-bbtest.yml)（本地测试 profile）—— 把 endpoint 改成你的真实 LLM 即可。

---

## 四、数据库 schema 变化

bbBot 启动时 `AiAgentSchemaInitializer` 会**自动**执行 `CREATE TABLE IF NOT EXISTS` 建以下新表（幂等，可重复跑）：

| 表 | 用途 | 大小预期 |
|---|---|---|
| `ai_user_role` | Agent 角色绑定 | 行数 = 用户数 × 角色 |
| `ai_tool_policy` | 工具策略 | 行数 = 工具数 × 角色 ≈ < 100 |
| `ai_tool_invocation_log` | 工具调用审计 | 高写入，建议定期归档 |
| `ai_cron_task` | 定时 Agent 任务 | 低 |
| `ai_memory_event` | **新**：完整事件流 | 高写入，每条消息一行 |
| `ai_memory_session` | **新**：会话边界 + summary | 中 |
| `ai_memory_fact` | **新**：长期事实库（ngram FULLTEXT） | 中 |

**老 `chat_history` 表**：保留只读（cutover 后**不再写入**）。建议保留至少 30 天以防回滚。

种子策略（自动插入）：13 个核心工具 + `load_skill` + 3 个 memory 工具默认对 `user` 角色开放，`shell_exec` / `file_write` 通过 `requiresOwner=true` 注解硬拦截。

### 关键：MySQL 8.0 ngram 索引

`ai_memory_fact.search_text` 上的 `FULLTEXT KEY ft_search WITH PARSER ngram` 索引是中文搜索关键。启动后验证：

```sql
SHOW CREATE TABLE ai_memory_fact\G
-- 期望看到：FULLTEXT KEY `ft_search` (`search_text`) WITH PARSER `ngram`
```

如果没有 ngram，FactStore 自动 fallback 到 LIKE 搜索（功能仍可用但效果差）。

---

## 五、升级步骤（按部署形态分）

### 形态 A：本地开发环境（推荐先在这里跑通）

```bash
cd ~/IdeaProjects/bbBot

# 1. 拉新分支
git fetch origin claude/gifted-tesla-c73b2e
git checkout claude/gifted-tesla-c73b2e

# 2. （仅一次）准备真实 LLM 配置
cp scripts/dev/real-llm.env.example scripts/dev/real-llm.env
$EDITOR scripts/dev/real-llm.env   # 取消注释你的 endpoint 段、填 key

# 3. 启动（自动建库 + 起 bbBot）
./scripts/dev/bb-dev.sh up

# 4. 验证
./scripts/dev/bb-dev.sh which-llm    # 确认接的是真实 endpoint
./scripts/dev/bb-dev.sh test         # 跑 13 个 A 场景（mock 模式才有意义；接真 LLM 可能部分宽松失败）

# 5. 进交互模式手动玩
./scripts/dev/bb-dev.sh repl
> 你好
> agent 现在几点
> agent 帮我记住：我是 splatoon3 老玩家
> :q
./scripts/dev/bb-dev.sh down
```

### 形态 B：已有 Docker 部署升级（misuaa/bb-bot 镜像）

```bash
cd ~/IdeaProjects/bbBot

# 1. 拉新分支
git fetch origin claude/gifted-tesla-c73b2e
git checkout claude/gifted-tesla-c73b2e

# 2. 备份生产 MySQL（强烈推荐）
ssh your-prod-host
mysqldump -u root -p bb_bot > bb_bot_backup_$(date +%Y%m%d).sql
exit

# 3. （仅一次）改你的生产 application.yml / application-prod.yml
# 关键：加 aiAgent.owners + chatGPT.streamEnabled 等（参考第三节）

# 4. 打包
mvn -pl bb-bot-server -am package -Dmaven.test.skip=true -P prod

# 5. 构建镜像（沿用现有 Dockerfile）
cd bb-bot-server
docker build -t misuaa/bb-bot:vX.Y.Z .
docker push misuaa/bb-bot:vX.Y.Z   # 如果需要推

# 6. 部署
docker pull misuaa/bb-bot:vX.Y.Z
docker stop bb-bot && docker rm bb-bot
docker run -d --name bb-bot \
  -v /your/config:/bot/config \
  -v /your/data/memory-workspace:/bot/memory-workspace \  # ★ 新：持久化记忆 workspace
  -v /your/data/plugins:/bot/plugins \                    # 可选：插件目录
  -v /your/data/skills:/bot/skills \                      # 可选：SKILL 目录
  --restart unless-stopped \
  misuaa/bb-bot:vX.Y.Z

# 7. 看日志确认建表完成
docker logs -f bb-bot | grep -E "AiToolRegistry|SkillRegistry|AI Agent schema"
```

**关键 volume**：
- `memory-workspace` 必须挂出来，否则容器重建后长期记忆丢失（不持久）
- `plugins` / `skills` 可选挂出便于热加载

### 形态 C：全新生产部署

按形态 B 流程做，但 step 2 备份跳过。

启动前确认：
- MySQL 8.0 已就绪（建库 `bb_bot`）
- `aiAgent.owners` 必填，否则没人能执行 admin 命令

---

## 六、启动后验收清单

按顺序确认每条都过：

### 6.1 启动日志 grep

```bash
# 1. WebSocket 服务起来（如果你用 BB 协议）
grep "WebSocket服务器启动成功" logs/bot.log

# 2. 工具注册表
grep "AiToolRegistry 注册完成" logs/bot.log
# 期望：共 13 个工具：[splatoon3_salmon_run, ..., search_memory, recall_experience, record_experience]

# 3. AI Agent schema
grep "AI Agent schema 检查 / 初始化完成" logs/bot.log

# 4. 沙箱后端
grep "AI Agent 沙箱后端" logs/bot.log
# 期望：bubblewrap（Linux 装了 bwrap）/ docker / noop

# 5. SkillRegistry
grep "SkillRegistry 注册完成" logs/bot.log

# 6. Spring 完整启动
grep "Started Application" logs/bot.log
```

### 6.2 数据库 sanity check

```sql
-- 表都建了
SHOW TABLES LIKE 'ai_%';
-- 期望 7 张：ai_user_role / ai_tool_policy / ai_tool_invocation_log / ai_cron_task /
--          ai_memory_event / ai_memory_session / ai_memory_fact

-- ngram 索引
SHOW CREATE TABLE ai_memory_fact\G
-- 期望：FULLTEXT KEY `ft_search` (`search_text`) WITH PARSER `ngram`

-- 种子策略
SELECT tool_name, role, allowed FROM ai_tool_policy;
-- 期望至少看到 server_time/http_fetch/search_memory 等 user 角色 allowed=1
```

### 6.3 功能 smoke test

| 测试 | 期望 |
|---|---|
| `@bot 你好` | 走聊天人格回复，**流式吐字** |
| `@bot agent 现在几点` | 调 `server_time` 工具，返回真实时间 |
| `@bot agent 抓一下 https://example.com 标题` | 调 `http_fetch`，返回 `Example Domain` |
| `@bot agent 帮我记住：我喜欢喷喷武器` | 调 `record_experience`，写入 `memory-workspace/user/<你的id>/experience/preferences.md` |
| **重启 bbBot** + `@bot agent 我喜欢什么武器` | LLM 看到 system prompt 里的 memory.md，自然答出"喷喷"（**跨进程持久**） |
| 非 owner 用户：`@bot agent 跑 ls /` | `permission_denied / requires_owner`，`ai_tool_invocation_log` 落 denied 一行 |
| owner：`@bot /aiAgent.memory.view` | 看自己当前 memory.md 内容 |
| owner：`@bot /aiAgent.memory.tail 20` | 看最近 20 条事件 |

### 6.4 30 分钟后再来看（记忆蒸馏）

```sql
-- 等 sessionGapMinutes (默认 30) 后
SELECT session_id, ended_at, summary IS NOT NULL AS has_summary
  FROM ai_memory_session ORDER BY id DESC LIMIT 5;
-- 期望：旧 session 的 ended_at 不空、summary 不空（LLM 蒸馏完成）

-- 然后查 facts
SELECT COUNT(*) FROM ai_memory_fact;
-- 期望：> 0（从 session summary 抽出的 Key Facts）

-- 查 memory.md 内容
cat memory-workspace/user/<你的id>/compiled/memory.md
-- 期望：4 段都不再是「（暂无）」
```

---

## 七、配置 owner 命令一览

部署完后，群里 @bot 发送：

```bash
# 角色管理（M4）
/aiAgent.role.grant <userId> <role>
/aiAgent.role.revoke <userId> <role>
/aiAgent.role.list <userId>
/aiAgent.audit <userId> [days=7]

# Cron（M6）
/aiAgent.cron.add "0 0 9 * * *" 今天 splatoon3 祭典播报
/aiAgent.cron.list
/aiAgent.cron.remove <id>
/aiAgent.cron.toggle <id>

# 插件（M7）
/aiAgent.plugin.list
/aiAgent.plugin.reload

# 记忆（M8）
/aiAgent.memory.tail [N]         # 看最近事件流
/aiAgent.memory.search <kw>      # 在事件里 LIKE 搜
/aiAgent.memory.session          # 当前 session_id
/aiAgent.memory.reset            # 切新 session（触发蒸馏）
/aiAgent.memory.rebuild          # 强制重编 memory.md
/aiAgent.memory.view             # 看自己当前 memory.md
```

所有 `/aiAgent.*` 命令仅 owner 可执行（在 `aiAgent.owners` 名单里的 user id）。

---

## 八、回滚

如果升级后出问题需要回到老版本：

```bash
# 1. 停 bbBot
docker stop bb-bot   # 或 ./scripts/dev/bb-dev.sh down

# 2. 切回老 commit
git checkout master   # 或具体老 tag

# 3. 还原 application.yml 里的 aiAgent.* 配置（如有改过）

# 4. （可选）数据库：保留新表，老 chat_history 表本来就被保留
#   - 新表如果不想要可以 DROP（不会影响老逻辑）
DROP TABLE ai_memory_event, ai_memory_session, ai_memory_fact,
           ai_tool_invocation_log, ai_tool_policy, ai_user_role, ai_cron_task;

# 5. 重建老镜像 + 重启
mvn -pl bb-bot-server -am package -Dmaven.test.skip=true -P prod
docker build -t misuaa/bb-bot:old-version bb-bot-server/
docker run -d --name bb-bot ... misuaa/bb-bot:old-version
```

**注意**：新代码写到 `chat_history` 的 cutover 是单向的—— M8.3 之后 bot 回复进 `ai_memory_event` 而非 `chat_history`。回滚后老的 chatHistorySummary 命令只能查到 cutover 之前的老数据；之间这段时间的对话在新表。

---

## 九、可能的运维要点

### 9.1 Token 消耗

| 来源 | 频率 | 单次大约 |
|---|---|---|
| 聊天 / agent 模式 LLM 调用 | 每次用户互动 | 1-3K tokens |
| Memory 注入到 system prompt | 同上 | +1-2K tokens（memory.md ≤ 6000 字符） |
| session 蒸馏 LLM | 每 session 结束 | ~500-800 tokens |
| compileLongterm LLM | 每天大约 1 次（fingerprint 跳重） | ~1-2K tokens |
| compileFacts LLM | 上游变化时 | ~500 tokens |

每用户每天大约消耗 **20-50K tokens**（中度使用）。DeepSeek 一天约 ¥0.05-0.15。

### 9.2 ai_memory_event 表归档

事件流写入量大，建议定期归档：

```sql
-- 例：保留 90 天，老数据归档到 ai_memory_event_archive
INSERT INTO ai_memory_event_archive SELECT * FROM ai_memory_event
  WHERE created_at < NOW() - INTERVAL 90 DAY;
DELETE FROM ai_memory_event WHERE created_at < NOW() - INTERVAL 90 DAY;
```

**不要**清空 `ai_memory_session`（即使 session 结束）—— MemoryCompiler 跨 1 年的 longterm 编译要用。

### 9.3 memory-workspace 备份

```bash
# 每天备份
tar czf memory-workspace-$(date +%Y%m%d).tgz memory-workspace/
# 上传到 S3 / OSS 等
```

experience 文件 + compiled 文件是用户长期记忆的核心，丢失就是用户画像清零。

### 9.4 调试 LLM 蒸馏

如果发现 memory.md 长期是「（暂无）」，定位顺序：

1. `SELECT * FROM ai_memory_session WHERE ended_at IS NULL;` —— session 没结束？
2. `SELECT * FROM ai_memory_session WHERE summary IS NULL AND ended_at IS NOT NULL;` —— 蒸馏失败？看 `MemoryCompiler.sweepEndedSessions` 日志
3. LLM 是否实际能输出 `## 重要事实` 块？真实 LLM 一般能，mock LLM 不行
4. `/aiAgent.memory.rebuild` 手动强制全量重编

### 9.5 LLM 切换

把 `chatGPT.url` / `chatGPT.model` / `chatGPT.apiKey` 改成另一个 OpenAI 兼容 endpoint 即可，不需要重新部署：

```yaml
chatGPT:
  url: https://api.deepseek.com/v1/chat/completions
  model: deepseek-chat
  apiKey: sk-xxxxx
```

热重载需要重启 bbBot 进程；如想运行时切，可走环境变量（参考本地 `scripts/dev/real-llm.env` 模式）。

---

## 十、文件与 commit 速查

| 想找什么 | 路径 |
|---|---|
| 总体设计 / Phase 1-2 思路 | `.ai-workflow/01-discover.md` |
| M1-M7 SHIP 总结 | `.ai-workflow/04-ship.md` |
| 本地 dev 环境 | `scripts/dev/` 整个目录，入口 `scripts/dev/bb-dev.sh` |
| 测试 profile 配置 | `bb-bot-server/src/main/resources/application-bbtest.yml` |
| 生产配置模板 | `bb-bot-server/src/main/resources/application.yml` |
| 全部 23 commits | `git log --oneline master..claude/gifted-tesla-c73b2e` |
| 三层记忆系统源码 | `bb-bot-server/src/main/java/com/bb/bot/aiAgent/memory/` |
| 13 个工具源码 | `bb-bot-server/src/main/java/com/bb/bot/aiAgent/tools/` |
| 沙箱源码 | `bb-bot-server/src/main/java/com/bb/bot/aiAgent/sandbox/` |
| Cron / 插件源码 | `bb-bot-server/src/main/java/com/bb/bot/aiAgent/{cron,plugin}/` |
| Owner 命令 handler | `bb-bot-server/src/main/java/com/bb/bot/handler/aiAgent/` |
| Schema 自动建表 | `bb-bot-server/src/main/java/com/bb/bot/aiAgent/auth/AiAgentSchemaInitializer.java` |

---

## 十一、合并到 master 建议

23 个 commits 都是顺序的、独立的、可逐个 cherry-pick 的。建议：

```bash
# 选项 A：rebase + merge（保留每个 milestone 的 commit 历史，便于回看 / cherry-pick）
git checkout master
git pull
git merge --ff-only claude/gifted-tesla-c73b2e   # 如果可以 fast-forward
# 或
git rebase claude/gifted-tesla-c73b2e

# 选项 B：squash（合成 1 个大 commit）
git checkout master
git merge --squash claude/gifted-tesla-c73b2e
git commit -m "feat(ai): bbBot AI Agent upgrade (M1-M8)"
```

**推荐选项 A**——历史完整，每个 milestone 独立可读，未来 git bisect / 回滚某个 milestone 都方便。

---

> 部署有疑问看：[`.ai-workflow/01-discover.md`](.ai-workflow/01-discover.md) 完整 plan，或 `scripts/dev/README.md` 本地测试指引。
> 紧急回滚：`git checkout master && docker run misuaa/bb-bot:<旧 tag>`，新表保留不动不影响。
