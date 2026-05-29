# bbBot 重构 — 原子任务拆分与自动化执行清单

> 版本：v1 ｜ 日期：2026-05-29 ｜ 依据：[`requirements.md`](./requirements.md) / [`design.md`](./design.md)
> 粒度（已确认）：**原子级** —— 一个原子改动 + 其测试 = 一个任务，独立可编译/可测/可提交。
> 执行模型（已确认）：主 agent 跟踪进度，按 DAG 派子 agent 实现+测试，**全程无人工干预**。

## 0. 执行环境与门槛（已核实）

| 项 | 值 |
|---|---|
| JDK | Corretto 17：`export JAVA_HOME=$(/usr/libexec/java_home -v 17)` |
| mvn | `/Users/renyuming/Documents/develop/maven/apache-maven-3.6.3/bin/mvn` |
| 本地仓库 | `-Dmaven.repo.local=/Users/renyuming/Documents/develop/maven/repository` |
| 编译/测试 | `mvn -Dmaven.javadoc.skip=true test`（surefire 3.2.5 已支持 JUnit5） |
| e2e 本地台 | `./scripts/dev/bb-dev.sh up --build && ./scripts/dev/bb-dev.sh test`（MySQL `misu-mysql-local`@3316 已健康运行；mock OpenAI 18800） |
| 测试栈 | JUnit5 + Mockito + AssertJ（`spring-boot-starter-test`，已在依赖中） |

**每个任务的验收门槛（自动）**：① 本地 `mvn test` 编译通过 + 相关单测全绿；② 不破坏既有绿基线；③ 提交独立 commit（`refactor(<TaskId>): ...`）。
**覆盖要求**：纯逻辑边界全覆盖；I/O 路径用以下手段**真实覆盖**——
- **渲染（ImageUtils/Renderer）**：golden-image 测试（渲染→与提交的参考 PNG 比对，允许像素级微差阈值，D2）。
- **AI 链路（Provider/Agent/Tool）**：`bb-dev.sh test` 端到端（mock OpenAI）+ 现有 Provider 单测。
- **HTTP（RestUtils）**：本地 stub server（JDK `com.sun.net.httpserver.HttpServer`）真实收发。
- **WebSocket 守护逻辑**：可控伪子类驱动循环判定（心跳/重连阈值）单测。
- **真实 QQ / 真实 NSO 账号链路**：无法在无人环境自动跑，**打 `@manual-verify` TODO 清单交付人工**（这是唯一允许的"非自动覆盖"，须显式列出，不得静默跳过）。

---

## 1. 原子任务总表（约 40 个）

> 列：任务号 · 内容 · owned 文件 · 依赖 · 测试要求。
> 同一波次内、文件不相交的任务可并行；触碰同一文件的任务在表内已串成依赖链。

### 波次 1（无外部依赖）

#### T1 统一回复入口（D1：工具 bean `BbReplies`）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T1.1 | 新建 `BbReplies` 组件（`atText/text/send`） | 新增 `common/.../BbReplies.java` | — | 单测：Mockito mock `BbMessageApi`，断言构造的 `BbSendMessage`；边界含 text=null/空串/超长、send 空列表 |
| T1.2 | 迁移 `BbNsoHandler` 至 `BbReplies` | `handler/nso/BbNsoHandler.java` | T1.1 | 该 handler 决策/回复用例 + 编译 |
| T1.3 | 迁移 `PokemonHandler` | `handler/pokemon/PokemonHandler.java` | T1.1 | 同上 |
| T1.4 | 迁移 `BbChatHistoryHandler` | `handler/chatHistory/BbChatHistoryHandler.java` | T1.1 | 同上 |
| T1.5 | 迁移 `BbAiPluginHandler` | `handler/aiAgent/BbAiPluginHandler.java` | T1.1 | 同上 |
| T1.6 | 迁移 `BbAiSkillHandler` | `handler/aiAgent/BbAiSkillHandler.java` | T1.1 | 同上 |
| T1.7 | 迁移 `BbAiAgentAdminHandler` | `handler/aiAgent/BbAiAgentAdminHandler.java` | T1.1 | 同上 |
| T1.8 | 迁移 `BbAiMemoryHandler` | `handler/aiAgent/BbAiMemoryHandler.java` | T1.1 | 同上 |
| T1.9 | 迁移 `BbAiBillingHandler` | `handler/aiAgent/BbAiBillingHandler.java` | T1.1 | 同上 |
| T1.10 | 迁移 `BbSplatoonHandler` | `handler/splatoon/BbSplatoonHandler.java` | T1.1 | 同上 |
| T1.11 | 迁移 `BbFortuneHandler` | `handler/fortune/BbFortuneHandler.java` | T1.1 | 同上 |
| T1.12 | 迁移 `OneBotMessageApi` | `api/oneBot/OneBotMessageApi.java` | T1.1 | 同上 |
| **T1.13** | 迁移 `BbSplatoonUserHandler.replyText` ★共享 T5 | `handler/splatoon/BbSplatoonUserHandler.java`（仅回复方法） | T1.1 | 编译 + 回复一致断言 |
| **T1.14** | 迁移 `BbAiChatHandler.sendReply/sendAtText` ★共享 T7 | `handler/aiChat/BbAiChatHandler.java`（仅回复方法） | T1.1 | `BbAiChatHandlerDecisionTest` 绿 |

#### T2 多渠道消息内容遍历去重
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T2.1 | 抽 `forEachContent(msg, onText,onLocalImage,onAt,onNetImage)` | 新增/渠道公共处 | — | 单测：每种内容类型回调被正确触发；空列表/未知类型边界 |
| T2.2 | `QqToBbMessageApi` 4 方法改用 | `api/qq/QqToBbMessageApi.java` | T2.1 | 单测 mock 上传，断言四渠道 payload 不变 |
| T2.3 | `DiscordMessageApi` 等改用 | `api/discord/DiscordMessageApi.java` | T2.1 | `DiscordMessageUtilTest` 扩展，断言文本/AT/图拼接不变 |

#### T3 AI Provider 公共化
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T3.1 | 抽 `RetryExecutor` | 新增 `provider/RetryExecutor.java` | — | 单测全分支：可重试/不可重试、达到 max、退避 `min(interval*mult,max)` 曲线、首次即成功 |
| T3.2 | 抽 `HttpErrorClassifier` | 新增 `provider/HttpErrorClassifier.java` | — | 单测：401/403→UNAUTHORIZED、429→RATE_LIMITED、500/503→RETRYABLE、400/404→FATAL |
| T3.3 | `AnthropicProvider` 委托公共类 | `provider/AnthropicProvider.java` | T3.1,T3.2 | 现有 provider 测试全绿 + 构造各状态码响应验证 |
| T3.4 | `OpenAiCompatProvider` 委托公共类 | `provider/OpenAiCompatProvider.java` | T3.1,T3.2 | `AbstractOpenAICompatibleProviderTest`/`OpenAIProviderTest` 绿 + e2e A1/A2/A3 |

#### T6 Splatoon Service JSON/资源去重（★须先于 T5）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T6.1 | battle impl：JSON 局部缓存 + `saveGearResource` | `database/splatoon/.../SplatoonBattleRecordServiceImpl.java` | — | 单测：喂样本 JSON 断言落库字段；mock `resourcesUtils` 断言资源路径/URL；null 分支 |
| T6.2 | coop impl：同上 | `database/splatoon/.../SplatoonCoopRecordsServiceImpl.java` | — | 同上 |

#### T8 魔法值常量化（QQ 部分★先于 T4.3；Splatoon 部分★先于 T5.3）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T8.1 | `QqOpcode`/`QqIntent` 枚举 + 替换 | `connection/qq/QqWebSocketClient.java`（仅 opcode/intents 区域）+ 新增枚举 | — | 单测断言枚举值 == 原字面量（10/0/11/7；1<<30/25/12） |
| T8.2 | `SplatoonModes` 常量 + 替换 ★共享 T5.3/T9.3 | `aiAgent/tools/SplatoonRecordTool.java`（仅模式 ID 定义） | — | 单测断言常量 == 原 Base64 串 |
| T8.3 | `ConfigKeys` 常量 + 替换引用 | 新增 `ConfigKeys` + 各引用处 | — | 单测断言常量值；编译 |

#### T9 工具层局部去重/现代写法（SplatoonRecordTool 部分★先于 T5.3）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T9.1 | `FileUtils` try-with-resources | `common/util/FileUtils.java` | — | 单测：临时文件读写/base64 往返；不存在文件/空文件边界；资源释放 |
| T9.2 | `RestUtils` 公共 header 构造 | `common/util/RestUtils.java` | — | 单测：本地 stub HttpServer 真实收发，断言 UA/Content-Type/参数序列化不变 |
| T9.3 | `SplatoonRecordTool` formatters ★共享 T8.2/T5.3 | `aiAgent/tools/SplatoonRecordTool.java`（仅 `formatDesc/battleJudgement/coopClear` 等） | T8.2 | 单测全分支：WIN/LOSE/null、各 desc 拼接、空 parts |

#### T10 BbEventDispatcher 修缮（同文件串行）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T10.1 | 正则预编译缓存 | `dispatcher/BbEventDispatcher.java` | — | 单测：MATCH/FUZZY/REGEX 匹配结果不变；同规则多次匹配不重复 compile（可注入计数验证） |
| T10.2 | `handlerExecute` 日志类名修正 + `InvocationTargetException` 取 cause | 同上 | T10.1 | 单测：故意抛异常，断言日志含真实 handler 类名 + 原始异常 |
| T10.3 | 匿名 `Runnable` → lambda | 同上 | T10.2 | 编译 + 既有分发用例 |

### 波次 2

#### T4 WebSocket 守护基类（T4.3 依赖 T8.1）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T4.1 | sdk 新增 `AbstractWebSocketGuard` | `bb-bot-sdk/.../client/AbstractWebSocketGuard.java` | — | 单测：伪子类驱动 `handleTick`/`interval`，验证心跳节奏、`shouldReconnect` 阈值、吞异常续跑 |
| T4.2 | `BbWebSocketClient` 继承基类 | `bb-bot-sdk/.../client/BbWebSocketClient.java` | T4.1 | `clientTest` 绿 + e2e（bb-dev 用 BB 协议握手即覆盖该客户端） |
| T4.3 | `QqWebSocketClient` 继承基类 | `connection/qq/QqWebSocketClient.java`（connectLoop 区域） | T4.1, T8.1 | 单测重连/心跳逻辑；`@manual-verify` 真实 QQ 链路 |

#### T7 大文件拆分（T7.4 依赖 T1.14）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T7.1 | `ImageUtils.getExtension` 提取（消除 7 处） | `common/util/ImageUtils.java` | — | 单测：含点/无点/多点/末尾点边界 |
| T7.2 | `createTranslucentCanvas` 提取（消除 3 处透明初始化） | 同上 | T7.1 | golden-image：透明 PNG 缩放不变黑 |
| T7.3 | `joinImages(...,boolean horizontal)` 合并横/纵拼接 | 同上 | T7.2 | golden-image：横/纵拼接结果与改前一致 |
| T7.4 | `BbAiChatHandler` early-return 扁平化 + `persistBotReply` 用 `joining` | `handler/aiChat/BbAiChatHandler.java`（控制流） | T1.14 | `BbAiChatHandlerDecisionTest` 绿 + e2e A1/A9 |
| T7.5 | 抽 `SplatoonStyleConfig`（两 renderer 共享配色/模式表） | 新增 + 两 renderer 引用点 | — | 单测断言映射值不变 |
| T7.6 | `SplatoonHtmlRenderer.battleCard` 拆方法 | `handler/splatoon/render/SplatoonHtmlRenderer.java` | T7.5 | golden-image：`SplatoonRenderSmokeTest`/`SplatoonHtmlDemoTest` 改造为断言型，比对参考图 |
| T7.7 | `SplatoonRecordRenderer.writeOneCoopRecord` 拆 `drawHeader/drawWeapons/drawPlayerBlock` | `handler/splatoon/render/SplatoonRecordRenderer.java` | T7.5 | golden-image 比对 |

### 波次 3

#### T5 Splatoon 打工/对战合并（收口，依赖 T1.13/T6/T8.2/T9.3）
| 任务 | 内容 | owned 文件 | 依赖 | 测试 |
|---|---|---|---|---|
| T5.1 | `RecordType` 枚举 + `RecordRangeParser` | 新增 | — | 单测全边界：无区间/单值/区间/超上限/格式错误/越界 |
| T5.2 | `BbSplatoonUserHandler` 合并打工/对战记录方法 | `handler/splatoon/BbSplatoonUserHandler.java`（记录方法） | T1.13, T5.1 | golden-image + `@manual-verify` 真实 NSO；含/不含区间/超限报错用例 |
| T5.3 | `SplatoonRecordTool` 合并 `recordList/recordDetail` | `aiAgent/tools/SplatoonRecordTool.java`（主流程） | T5.1, T8.2, T9.3 | 单测查询条件构造；`SplatoonRecordSelectorCheck` 改断言型 |
| T5.4 | Service 同构上提公共基类 | `database/splatoon/.../*ServiceImpl.java` + 新增基类 | T5.1, T6.1, T6.2 | 单测 battle/coop 复用基类后映射不变 |

---

## 2. 执行 DAG（拓扑序，主 agent 据此调度）

```
波次1（可高度并行，仅同文件链串行）：
  T1.1 →（并行）T1.2..T1.14
  T2.1 →（并行）T2.2, T2.3
  T3.1, T3.2 →（并行）T3.3, T3.4
  T6.1, T6.2
  T8.1 ; T8.2 ; T8.3
  T9.1 ; T9.2 ; T8.2→T9.3
  T10.1 → T10.2 → T10.3

波次2：
  T4.1 →（并行）T4.2 ;  (T8.1 & T4.1) → T4.3
  T7.1→T7.2→T7.3 ;  T1.14→T7.4 ;  T7.5→（并行）T7.6, T7.7

波次3：
  T5.1 ;  (T1.13&T5.1)→T5.2 ;  (T5.1&T8.2&T9.3)→T5.3 ;  (T5.1&T6.1&T6.2)→T5.4
```

**关键串行约束（共享文件，跨波次不得并行编辑）**：
- `SplatoonRecordTool.java`：T8.2 → T9.3 → T5.3（依次提交）
- `BbSplatoonUserHandler.java`：T1.13 → T5.2
- `BbAiChatHandler.java`：T1.14 → T7.4
- `QqWebSocketClient.java`：T8.1 → T4.3
- 两 Splatoon ServiceImpl：T6.x → T5.4
- `BbEventDispatcher.java`：T10.1 → T10.2 → T10.3

## 3. 子 agent 工作协议（每个原子任务）

每个子 agent 收到一个任务，须完成闭环：
1. 读相关文件，按 design.md 的该单元设计实现**该原子改动**（不越界改禁区文件/方法）。
2. 编写/补充**单元测试**，满足该任务"测试"列要求；I/O 按 §0 手段真实覆盖；无法自动的列入 `@manual-verify`。
3. 先 `git add -A && git commit`（保留可回溯点），再本地 `mvn -Dmaven.javadoc.skip=true test` 验证。
4. 失败则修复并再次提交；通过后返回结构化结果（改动文件、新增测试、覆盖说明、manual-verify 项、是否绿）。
5. **不得 push、不得 merge master**（会触发生产部署）。

## 4. manual-verify 汇总（交付时输出，人工补验）

> 自动流程结束后统一汇总，至少包含：
- T4.3 真实 QQ WebSocket 重连/心跳
- T5.2 真实 NSO 拉取 → 对战/打工记录渲染图（用 `bb-dev repl` 或生产灰度）
- T7.6/T7.7 渲染图人工视觉确认（golden 比对已自动，但首次参考图需人工认可）
