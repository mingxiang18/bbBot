# bbBot

- 使用Java语言开发的个人自用misu-bb机器人(开发中)
- 目前支持（OneBot11协议、QQNT、QQ官方机器人、Telegram、Discord、BB 私有协议），初步开发中
- docker镜像地址：https://hub.docker.com/r/misuaa/bb-bot

### 模块划分

- bb-bot-sdk sdk模块（提供bb私有协议、外部机器人协议相关实体，提供事件和具体可执行操作的api）
- bb-bot-server 机器人服务模块（与不同协议机器人建立连接，并进行机器人具体功能的实现）

### 环境相关
- 开发语言：Java
- 开发环境：JDK17
- 基本框架：SpringBoot3、Mybatis
- 数据库：MySQL8（必须 8.0+，记忆系统的 ngram FULLTEXT 需要）

### 实现功能
- 每日抽签
- Nintendo Switch Online登录和数据获取
- Splatoon3地图、活动、祭典获取
- Splatoon3个人数据记录和获取
- AI聊天（**新**：流式 + 长期记忆）
- AI Agent（**新**：远程派活、工具调用、SKILL 体系、跨进程持久记忆）
- 日语学习相关
- 海龟汤
- 聊天历史消息
- 跑团骰子投掷
- 答案之书
- 赛马小游戏
- 俄罗斯转盘

---

## AI Agent 升级（M1-M8）

bbBot 现在不只是聊天机器人，还是一个 **完整的 IM Agent**，对标 [openhanako](https://github.com/liliMozi/openhanako) / [openclaw](https://github.com/openclaw/openclaw)。

### 核心能力一览

| 能力 | 命令 / 示例 | 说明 |
|---|---|---|
| 🌊 流式聊天 | 任意 `@bot 你好` | TG / Discord / BB 字逐渐冒出来；OneBot / QQ 协议不支持 edit，自动降级为完整回复后一次发（不强开）|
| 🤖 派活 Agent | `@bot agent 抓一下 https://example.com 的标题` | AI 自主调工具完成任务，function calling 循环上限 10 步 |
| 🧠 长期记忆 | `@bot agent 记住我是 splatoon3 老玩家` | 4 阶段编译 + 跨进程持久；重启后 AI 仍记得 |
| 🔧 13 个内置工具 | `server_time / http_fetch / file_read / list_dir / file_write / web_search / grep_search / shell_exec / search_memory / recall_experience / record_experience / load_skill / splatoon3_salmon_run` | 通用原语 + 业务专用工具 |
| 📜 SKILLS 体系 | 在 `skills/<name>/SKILL.md` 写 markdown 剧本 | 零代码扩展 AI 能力 |
| 🔌 插件系统 | 丢 `*.jar` 到 `plugins/` 目录 | `plugin.json` 声明工具类，URLClassLoader 隔离 |
| 🔒 OS 沙箱 | `shell_exec` 走 Bubblewrap / Docker | 高危工具强制隔离 |
| ⏰ Cron 定时任务 | `/aiAgent.cron.add "0 0 9 * * *" 早报` | Spring CronExpression 格式 |
| 👤 角色 + 审计 | `/aiAgent.role.grant @user admin` | MySQL 角色 + 工具策略表 + 完整审计日志 |

### Owner 管理命令一览

```bash
# 角色
/aiAgent.role.grant <userId> <role>
/aiAgent.role.revoke <userId> <role>
/aiAgent.role.list <userId>
/aiAgent.audit <userId> [days=7]

# Cron 定时任务
/aiAgent.cron.add "0 9 * * * *" 每天 9 点播报
/aiAgent.cron.list
/aiAgent.cron.remove <id>

# 插件
/aiAgent.plugin.list
/aiAgent.plugin.reload

# 记忆系统
/aiAgent.memory.tail [N]
/aiAgent.memory.search <kw>
/aiAgent.memory.session
/aiAgent.memory.reset
/aiAgent.memory.rebuild
/aiAgent.memory.view
```

### 部署 / 升级

完整部署文档：**[UPGRADE.md](UPGRADE.md)** —— 包含：
- 全部 23 个 commits 摘要
- application.yml 配置变更
- MySQL schema 自动建表说明
- 三种部署形态（本地开发 / Docker 升级 / 全新生产）
- 启动后验收清单
- 回滚步骤
- 运维要点（token 消耗、归档、备份）

### 本地开发测试

```bash
# 一行启动（依赖 misu-server 的 MySQL 3316 容器）
./scripts/dev/bb-dev.sh up

# 验证当前接的是 mock 还是真实 LLM
./scripts/dev/bb-dev.sh which-llm

# 跑 13 个 A 场景回归
./scripts/dev/bb-dev.sh test

# 交互式手动玩
./scripts/dev/bb-dev.sh repl
```

详细见 [scripts/dev/README.md](scripts/dev/README.md)。

### 接真实 LLM

支持任一 OpenAI 兼容 endpoint（必须支持 function calling）：

```bash
cp scripts/dev/real-llm.env.example scripts/dev/real-llm.env
# 编辑 real-llm.env 填好 CHATGPT_API_KEY / CHATGPT_URL / CHATGPT_MODEL
./scripts/dev/bb-dev.sh up   # 自动 source
```

四个推荐 endpoint（模板里有）：
- **DeepSeek**（国内直连、便宜）：`https://api.deepseek.com/v1/chat/completions`
- **OpenAI**：`https://api.openai.com/v1/chat/completions`
- **Moonshot Kimi**：`https://api.moonshot.cn/v1/chat/completions`
- **通义千问**：`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`

### 给开发者：扩展 AI 能力的三条路

1. **新建 @AiTool（Java，重启生效）**：写一个 `@AiTool(name, description)` 注解的 Spring bean 方法，启动期自动注册
2. **写 SKILL（markdown，热加载）**：`skills/<name>/SKILL.md` 写 YAML frontmatter + 指引，重启或 `/aiAgent.plugin.reload` 即生效
3. **打成插件 jar（独立 ClassLoader）**：带 `plugin.json` 的 jar 丢到 `plugins/`，`@AiTool` 自动注册

详细的 AI Agent 架构看 [`.ai-workflow/01-discover.md`](.ai-workflow/01-discover.md)。
