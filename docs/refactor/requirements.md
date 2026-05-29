# bbBot 代码精简与重构 — 需求文档

> 版本：v1 ｜ 日期：2026-05-29 ｜ 性质：纯文档，不含代码改动
> 配套：详细设计见 [`design.md`](./design.md)

## 1. 背景与目标

bbBot（`bb-bot-server` ~3.3 万行 + `bb-bot-sdk`）经过长期迭代，业务层、工具层、连接层积累了较多**重复代码、超大文件、魔法值与冗长样板**。本次重构目标：

- **消除重复**（DRY）：把跨文件复制的逻辑收敛到单一实现。
- **拆分大方法/大类**：降低单文件/单方法复杂度，便于阅读与测试。
- **提升可读性**：统一命名、early-return、消除魔法值、修正错误日志。
- **引入更简洁写法**：在不损害可读性的前提下用 try-with-resources / Stream / Optional / 查表替换样板。

**非功能约束**：本次重构**不改变任何对外行为**（消息回复内容、图片渲染结果、API 行为保持一致）。

## 2. 范围

### 2.1 纳入（10 个主题）

| 编号 | 主题 | 维度 | 风险 |
|---|---|---|---|
| T1 | 统一消息回复样板（`@用户+文本`） | 消除重复 | 低 |
| T2 | 多渠道消息内容遍历去重 | 消除重复 | 低 |
| T3 | 两个 AI Provider 公共化（重试/错误分类/流式） | 消除重复 | 中 |
| T4 | 两个 WebSocketClient 守护线程基类 | 消除重复 | 中 |
| T5 | Splatoon「打工/对战」平行逻辑合并（Handler/Tool/Service 三层） | 消除重复 | 中 |
| T6 | Splatoon Service 深层 JSON 取值 + 资源缓存去重 | 消除重复 / 可读性 | 中 |
| T7 | 超大文件拆分（ImageUtils / BbAiChatHandler / 各 Renderer） | 拆分大类 | 中~高 |
| T8 | 魔法值常量化（QQ opcode/intents、Splatoon 模式 ID、配置键） | 可读性 | 低 |
| T9 | 工具层局部去重与现代写法（FileUtils/RestUtils/SplatoonRecordTool 等） | 简洁写法 | 低 |
| T10 | BbEventDispatcher 修缮（正则预编译、日志定位、lambda） | 可读性 / 缺陷 | 低~中 |

### 2.2 排除

- **T11（database 层空 Service/Impl 样板）不做**：25 个空接口 + 14 个空 impl 虽是样板，但被各处 `@Autowired` 注入，移除收益偏低、面广，本次明确排除。
- 不引入新框架、不升级 Spring/MyBatis-Plus 大版本。
- 不改数据库 schema、不改部署清单（`bb-bot.yaml` 等）。

## 3. 各主题需求明细

> 每条含：**现状问题 / 目标 / 验收标准**。代表性位置见设计文档，行号以实施时为准。

### T1 统一消息回复样板
- **现状**：`@用户 + 文本` 回复三行代码散落 20+ 处；9 个 handler 各写了同义辅助方法，命名还不一致（`reply` / `sendAtText` / `replyText`）；部分 handler 直接内联 `buildAtMessageContent`。无公共基类。
- **目标**：提供单一回复入口，所有 handler/API 复用；删除各自的重复辅助方法。
- **验收**：① 全项目仅保留一处 `@+文本` 构造实现；② 各 handler 不再有私有 `reply/sendAtText/replyText`；③ 回复内容与重构前逐字一致。

### T2 多渠道消息内容遍历去重
- **现状**：`QqToBbMessageApi` 的 4 个发送方法各有一份"遍历 `BbMessageContent` 按 TEXT/IMAGE/AT 分支"循环；Discord 等渠道亦各有一份。
- **目标**：抽公共遍历/分发逻辑，渠道差异以回调/访问者外置。
- **验收**：① 单一遍历实现；② 各渠道发送行为与重构前一致（文本拼接、图片上传、AT 格式不变）。

### T3 AI Provider 公共化
- **现状**：`AnthropicProvider` 与 `OpenAiCompatProvider` 的 `executeWithRetry`（指数退避）与 `classify`（HTTP 状态→错误类型）逻辑**完全一致**，`doStreamCall` 结构高度相似。
- **目标**：抽公共重试执行器与 HTTP 错误分类器，两个 Provider 共用。
- **验收**：① 重试次数/退避曲线/错误分类与重构前一致；② 两个 Provider 不再各自维护重试与分类代码；③ 现有 Provider 单测全绿。

### T4 WebSocketClient 守护线程基类
- **现状**：`connection/qq/QqWebSocketClient` 与 sdk `client/BbWebSocketClient` 各有一份 `connectLoop`（心跳/重连判断/sleep/吞异常）。
- **目标**：抽模板基类，心跳与重连条件留抽象方法；QQ 版"已连接即发心跳"差异参数化。
- **验收**：① 重连间隔、心跳节奏、关闭行为与重构前一致；② 跨模块共享方式不破坏 sdk 的对外可用性（基类须置于 sdk 才能共享）。

### T5 Splatoon 打工/对战合并（主题级整块）
- **现状**：同一套"解析区间→校验上限→查 DB→渲染"在三层各写两遍：
  - Handler：`BbSplatoonUserHandler` 打工 vs 对战
  - Tool：`SplatoonRecordTool.recordList` vs `recordDetail`
  - Service：`SplatoonBattleRecordServiceImpl` vs `SplatoonCoopRecordsServiceImpl`
- **目标**：用 `enum RecordType{COOP,BATTLE}` 承载差异（表/字段/渲染器），主流程统一；区间解析抽公共 parser。
- **验收**：① 打工记录、对战记录两条链路的**实测输出图片/文本**与重构前一致（须走活体链路验证，见全局约束）；② 三层不再有 coop/battle 平行复制。

### T6 Splatoon Service JSON/资源缓存去重
- **现状**：`SplatoonBattleRecordServiceImpl` 中 2~3 层链式 `getJSONObject(...).getJSONObject(...)` + null 检查反复出现；装备资源缓存 `getOrAddStaticResourceFromNet(...)` 对头/衣/鞋重复 8 次。
- **目标**：抽局部变量缓存中间 JSON 对象；抽 `saveGearResource(category, gearObj)`。
- **验收**：① 解析结果与落库字段不变；② 资源缓存路径/URL 取值不变。

### T7 超大文件拆分（主题级整块）
- **现状**：`ImageUtils`(826)、`BbAiChatHandler`(695)、`SplatoonHtmlRenderer`(575)、`SplatoonRecordRenderer`(445) 职责混杂、方法过长、坐标魔法值密集；`joinImagesHorizontal/Vertical` 95% 相同；透明画布初始化重复 3 次；`aiChatHandle` 多层嵌套。
- **目标**：按职责拆类/拆方法 + 提取重复片段（透明画布、文件后缀、拼接、渲染块）；`aiChatHandle` early-return 扁平化；颜色/模式映射在两个 renderer 收敛为 `SplatoonStyleConfig`。
- **验收**：① 渲染图片像素级一致（或视觉一致并经实测确认）；② AI 聊天回复行为不变；③ 单方法行数显著下降。

### T8 魔法值常量化
- **现状**：QQ 协议 `op==10/0/11/7`、`intents=1<<30|1<<25|1<<12` 裸数字；Splatoon Base64 模式 ID 散落；配置键 `"NSO"/"autoUploadRecords"/"session_token"` 硬编码。
- **目标**：引入 `enum QqOpcode/QqIntent`、Splatoon 模式 ID 常量、`ConfigKeys` 常量。
- **验收**：① 数值/字符串与重构前完全一致；② 协议/查询行为不变。

### T9 工具层局部去重与现代写法
- **现状**：`FileUtils` 手写 try-finally-close；`RestUtils` 请求头 UA/Content-Type 重复 4 次；`SplatoonRecordTool` 的 `formatDesc`/`battleJudgement` 等 if-else 链与字符串拼接重复。
- **目标**：try-with-resources；公共 header 构造；查表/Optional/抽公共 `formatDesc`。
- **验收**：① 资源正确释放，无新增 NPE 路径；② 解析/请求结果不变。

### T10 BbEventDispatcher 修缮
- **现状**：① REGEX 规则分支**每条消息都 `Pattern.compile`**（性能问题）；② `handlerExecute` 日志用 `method.getClass().getName()`，打印的是 `Method` 类名而非业务 handler 类名（**日志定位失效**），且未区分 `InvocationTargetException`；③ 两处匿名 `Runnable` 可改 lambda。
- **目标**：预编译并缓存正则；修正日志取 `instance.getClass().getSimpleName()` 并取 `getCause()`；匿名类改 lambda。
- **验收**：① 规则匹配结果不变；② 异常日志能正确定位到业务 handler 与原始异常；③ 高频消息下不再重复编译正则。

## 4. 全局约束（来自 CLAUDE.md，强制）

1. **push/merge 到 master 会自动构建并部署生产**。所有重构在当前 worktree 分支进行，**本地编译验证通过**后再考虑合并。
2. **改完先 commit 再验证**（保留可回溯提交点）。
3. **本地编译**：`mvn` 不在 PATH，用绝对路径 + `JAVA_HOME` 17 + 本地仓库参数。
4. **行为等价类（T3/T5/T6/T7）需走活体链路实测**，不能只靠"编译通过 + 组件健康"判定可用（尤其喷喷战绩渲染链路）。
5. 不触碰 `DockerfileLocal` 的 base 镜像行、`bb-bot.yaml` 占位行、sdk 的 javadoc 插件相关配置。

## 5. 已决策事项（用户已确认）

- **D1（T1 实现方式）= 工具 bean / 静态工具类**。新增 `BbReplies` 组件，handler 注入调用；不采用抽象 `BaseHandler` 继承（避免侵入所有 handler 类声明）。
- **D2（验收"一致"口径）= 视觉一致 + 实测**。T5/T7 渲染类以"实跑活体链路、人工/实测确认视觉效果一致"为准，允许像素级微差，不要求字节级完全一致。
