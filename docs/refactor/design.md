# bbBot 代码精简与重构 — 详细设计文档

> 版本：v1 ｜ 日期：2026-05-29 ｜ 配套需求：[`requirements.md`](./requirements.md)
> 拆分原则（已与用户确认）：**主题为单元 + 依赖排序**。允许多单元触及同一文件，但通过
> ①明确每单元在共享文件中的**职责边界（owned 区域 + 禁区）**、②显式的**执行顺序**来消除冲突。

---

## 0. 阅读指引

- 第 1 节：**工作单元总表**（10 个，对应 T1~T10）。
- 第 2 节：**共享文件矩阵** —— 哪些文件被多个单元触碰，各自负责哪部分。
- 第 3 节：**依赖 DAG 与执行波次** —— 真正保证"不重合"的关键。
- 第 4 节：**每个单元的详细设计**（owned 文件、边界、接口/伪代码、步骤、验证）。
- 第 5 节：**全局验证与回滚**。

---

## 1. 工作单元总表

| 单元 | 名称 | 主要落点 | 风险 | 依赖（须先完成） |
|---|---|---|---|---|
| **T1** | 统一消息回复入口 | `handler/**`、`api/oneBot` | 低 | 无 |
| **T2** | 多渠道消息内容遍历去重 | `api/qq`、`api/discord` 等 | 低 | 无 |
| **T3** | AI Provider 公共化 | `common/util/aiChat/provider` | 中 | 无 |
| **T4** | WebSocket 守护线程基类 | `connection/qq`、sdk `client` | 中 | T8 |
| **T5** | Splatoon 打工/对战合并 | `handler/splatoon`、`aiAgent/tools`、`database/splatoon` | 中 | T1, T6, T8, T9 |
| **T6** | Splatoon Service JSON/资源去重 | `database/splatoon/service/impl` | 中 | 无 |
| **T7** | 超大文件拆分 | `common/util/ImageUtils`、`handler/aiChat`、`handler/splatoon/render` | 中~高 | T1 |
| **T8** | 魔法值常量化 | `connection/qq`、`aiAgent/tools`、配置键多处 | 低 | 无 |
| **T9** | 工具层局部去重/现代写法 | `common/util/FileUtils`、`RestUtils`、`aiAgent/tools` | 低 | 无 |
| **T10** | BbEventDispatcher 修缮 | `dispatcher` | 低~中 | 无 |

---

## 2. 共享文件矩阵（冲突点 + 边界划分）

> 只列**被 ≥2 个单元触碰**的文件。其余文件由单一单元独占，天然无冲突。

| 共享文件 | 涉及单元 | 各单元的职责边界（owned 区域） | 禁区约定 |
|---|---|---|---|
| `handler/splatoon/BbSplatoonUserHandler.java` | T1, T5 | **T1**：仅删除/替换本类的 `replyText` 及其调用为统一入口。**T5**：仅重写打工/对战记录方法体。 | T5 改记录方法时**必须调用 T1 已落地的统一回复 API**，不得再引入新的内联回复。 |
| `handler/aiChat/BbAiChatHandler.java` | T1, T7 | **T1**：删除 `sendReply`/`sendAtText`，调用点改为统一入口。**T7**：`aiChatHandle` early-return 扁平化、`persistBotReply` 改 `joining`、其余拆方法。 | T7 不动回复辅助方法（归 T1）；T1 不动 `aiChatHandle` 控制流（归 T7）。 |
| `aiAgent/tools/SplatoonRecordTool.java` | T5, T8, T9 | **T8**：仅抽取 Base64 模式 ID 为常量并替换引用。**T9**：仅 `formatDesc`/`battleJudgement`/`coopClear` 等局部方法。**T5**：合并 `recordList`/`recordDetail` 主流程。 | T5 引用 T8 的模式 ID 常量、T9 的 `formatDesc`，不得回退为字面量/复制实现。 |
| `database/splatoon/service/impl/SplatoonBattleRecordServiceImpl.java`、`SplatoonCoopRecordsServiceImpl.java` | T5, T6 | **T6**：在**各自 impl 内**抽 JSON 局部变量缓存 + `saveGearResource` 私有方法（不跨类）。**T5**：把两个 impl 的同构逻辑上提到公共基类/工具。 | T5 复用 T6 抽好的私有方法上提，不得绕过重新内联。 |
| `connection/qq/QqWebSocketClient.java` | T4, T8 | **T8**：仅抽 `op`/`intents` 为枚举常量并替换。**T4**：仅把 `connectLoop` 守护逻辑上提到基类。 | 两者作用区域不同（`handleMessage` 的 opcode 分支 vs `connectLoop`）；T4 在 T8 之后开始，避免对同文件并发改动。 |

**独占文件（无冲突，简列）**：
- T2 → `api/qq/QqToBbMessageApi.java`、`api/discord/DiscordMessageApi.java` 等渠道 API。
- T3 → `common/util/aiChat/provider/AnthropicProvider.java`、`OpenAiCompatProvider.java`（+ 新增公共类）。
- T7 → `common/util/ImageUtils.java`、`handler/splatoon/render/SplatoonHtmlRenderer.java`、`SplatoonRecordRenderer.java`、`ScheduleMapRenderer.java`。
- T9 → `common/util/FileUtils.java`、`RestUtils.java`、`MessageBuilder.java`。
- T10 → `dispatcher/BbEventDispatcher.java`。
- T1 → 新增统一回复组件 + 其余 handler（`BbNsoHandler`、`PokemonHandler`、`BbChatHistoryHandler`、`BbAiPluginHandler`、`BbAiSkillHandler`、`BbAiAgentAdminHandler`、`BbAiMemoryHandler`、`BbAiBillingHandler`、`BbSplatoonHandler`、`BbFortuneHandler`、`api/oneBot/OneBotMessageApi`）。

---

## 3. 依赖 DAG 与执行波次

```
              无依赖（可并行）
   ┌─────────────────────────────────────────────┐
   │  T1   T2   T3   T6   T8   T9   T10           │   ← 波次 1
   └───┬──────────────┬─────┬──────┬──────────────┘
       │              │     │      │
       │ (BbAiChat)   │(QQ) │      │
       ▼              ▼     ▼      │
      T7             T4 ◄───┘      │   ← 波次 2  (T7 依赖 T1；T4 依赖 T8)
                                   │
   T1 ─┐  T6 ─┐  T8 ─┐  T9 ─┐      │
       └──────┴──────┴──────┴──────┴──► T5   ← 波次 3 (T5 依赖 T1/T6/T8/T9)
```

**执行顺序（推荐）**：

| 波次 | 单元 | 说明 |
|---|---|---|
| **波次 1** | T1, T2, T3, T6, T8, T9, T10 | 彼此无共享文件冲突，可任意顺序/并行。建议先做风险最低的 T8/T9/T10/T2 练手，再做 T1/T3/T6。 |
| **波次 2** | T7（须在 T1 之后）、T4（须在 T8 之后） | 仅与各自前置单元共享文件。 |
| **波次 3** | T5 | 收口单元：消费 T1（回复 API）、T6（impl 私有方法）、T8（模式 ID 常量）、T9（formatDesc）。最后做，风险最高，必须活体实测。 |

> **强约束**：跨波次的共享文件不得"并行编辑"。例如 `BbAiChatHandler` 必须 T1 改完并 commit 后 T7 再开始；`SplatoonRecordTool` 必须 T8、T9 改完并 commit 后 T5 再开始。每个单元独立 commit，便于回滚与定位。

---

## 4. 各单元详细设计

> 每单元给出：**owned 文件 / 边界 / 设计要点（接口或伪代码）/ 步骤 / 验证**。
> 接口签名为设计建议，命名实施时可微调，但职责边界不变。

### T1 统一消息回复入口
- **owned 文件**：新增 1 个组件 + 第 2 节列出的全部 handler/API 调用点；在 `BbSplatoonUserHandler`/`BbAiChatHandler` 中仅限"回复辅助方法及其调用"。
- **设计要点（D1 已定：工具 bean）**：
  ```java
  @Component
  public class BbReplies {
      private final BbMessageApi messageApi;
      // @用户 + 文本
      public void atText(BbReceiveMessage src, String text) { ... }
      // 纯文本（无 @）
      public void text(BbReceiveMessage src, String text) { ... }
      // 自定义内容列表
      public void send(BbReceiveMessage src, List<BbMessageContent> contents) { ... }
  }
  ```
  （已排除抽象 `BaseHandler` 继承方案：handler 由 Spring 实例化 + dispatcher 反射调注解方法，强制继承侵入性更大。）
- **步骤**：① 建组件并写单测覆盖 atText/text/send；② 逐 handler 替换私有 `reply/sendAtText/replyText` 与内联构造；③ 删除各自冗余辅助方法。
- **验证**：编译；抽样对比替换前后生成的 `BbSendMessage` 结构一致（可临时打印或单测断言）。

### T2 多渠道消息内容遍历去重
- **owned 文件**：`api/qq/QqToBbMessageApi.java`、`api/discord/DiscordMessageApi.java`（及其他存在同构循环的渠道 API）。
- **设计要点**：
  ```java
  // 公共遍历，渠道差异通过回调外置
  protected void forEachContent(BbSendMessage msg,
                                Consumer<String> onText,
                                Consumer<File> onLocalImage,
                                Consumer<String> onAt,
                                Consumer<String> onNetImage) { ... }
  ```
  各渠道在自己的 `send*` 里只实现回调体（QQ 走 media 上传、Discord 走 `<@id>` 拼接）。
- **步骤**：① 抽 `forEachContent`（可放渠道公共基类或工具）；② 四个 QQ 发送方法改为调用 + 回调；③ Discord 等同步收敛。
- **验证**：对每种内容类型（纯文本/本地图/AT/网络图）各发一条，比对发出的 payload 与重构前一致。

### T3 AI Provider 公共化
- **owned 文件**：`provider/AnthropicProvider.java`、`OpenAiCompatProvider.java`，新增 `RetryExecutor`、`HttpErrorClassifier`（同包）。
- **设计要点**：
  ```java
  public final class RetryExecutor {
      public <T> T execute(RetryConfig cfg, Supplier<T> task) throws AIException { ... }
      // 保留指数退避：interval = min(interval*multiplier, maxInterval)
  }
  public final class HttpErrorClassifier {
      public static AIException classify(int status, String tag, String body) {
          // 401/403→UNAUTHORIZED, 429→RATE_LIMITED, >=500→RETRYABLE, else FATAL
      }
  }
  ```
  `doStreamCall` 相似部分（建请求/发送/解析 SSE）可抽 `provider` 内部 helper，但**保持各 Provider 的协议差异**（Anthropic vs OpenAI 报文格式）独立。
- **步骤**：① 抽两个公共类 + 单测（覆盖各状态码与退避次数）；② 两个 Provider 改为委托；③ 跑现有 Provider 测试。
- **验证**：现有 `AbstractOpenAICompatibleProviderTest` 等单测全绿；构造 429/500/401 响应验证分类与重试一致。

### T4 WebSocket 守护线程基类（依赖 T8）
- **owned 文件**：sdk `client/BbWebSocketClient.java`、`connection/qq/QqWebSocketClient.java` 的 `connectLoop` 区域，新增基类（**置于 sdk 模块**以便 server 复用）。
- **设计要点**：
  ```java
  public abstract class AbstractWebSocketGuard extends WebSocketClient {
      protected final void startGuard() { /* while: handleTick(); sleep(interval()) 吞异常 */ }
      protected abstract long interval();
      protected abstract void handleTick(); // 子类实现心跳/重连判断
  }
  ```
  QQ 版"已连接即发心跳"作为 `handleTick` 的子类实现差异。
- **步骤**：① sdk 加基类；② 两端继承并实现 `handleTick`/`interval`；③ 保留各自 `shouldReconnect` 阈值。
- **验证**：本地启动观察重连/心跳日志节奏与改前一致；注意 sdk 改动若对外发布需同步 misu-server 缓存（见 CLAUDE.md）。

### T5 Splatoon 打工/对战合并（依赖 T1/T6/T8/T9，最后做）
- **owned 文件**：`handler/splatoon/BbSplatoonUserHandler.java`（记录方法体）、`aiAgent/tools/SplatoonRecordTool.java`（`recordList`/`recordDetail` 主流程）、`database/splatoon/service/impl/*`（同构逻辑上提）。
- **设计要点**：
  ```java
  enum RecordType { COOP, BATTLE }   // 承载表/字段/渲染器差异
  // Handler 层：区间解析抽公共 parser（消除两份正则）
  RecordRange parseRange(String msg, int min, int max, int maxSpan);
  // 主流程统一：解析 → 校验 → 查询(type) → 渲染(type)
  ```
- **步骤**：① 先确认 T1/T6/T8/T9 已 commit；② 抽 `RecordType` 与 `RecordRangeParser`；③ Handler 两方法合并；④ Tool 两方法合并；⑤ Service 同构上提（复用 T6 私有方法）。
- **验证（强制活体实测）**：实跑「打工记录」「对战记录」「打工记录2-4」等指令，对比重构前后**生成图片**与文本一致；至少覆盖含/不含区间、超上限报错两类。

### T6 Splatoon Service JSON/资源去重
- **owned 文件**：`SplatoonBattleRecordServiceImpl.java`、`SplatoonCoopRecordsServiceImpl.java`（仅类内私有方法，不跨类上提——上提归 T5）。
- **设计要点**：
  ```java
  JSONObject myTeam = battleRecord.getJSONObject("myTeam");
  if (myTeam != null) { JSONObject result = myTeam.getJSONObject("result"); ... }
  private void saveGearResource(String category, JSONObject gear) {
      if (gear == null) return;
      resourcesUtils.getOrAddStaticResourceFromNet(
          "nso_splatoon/" + category + "/" + gear.getString("name") + ".png",
          gear.getJSONObject("originalImage").getString("url"));
  }
  ```
- **步骤**：① 链式取值改局部变量缓存；② 头/衣/鞋三次调用改 `saveGearResource`。
- **验证**：插入一条真实战绩/打工 JSON，比对落库字段与缓存资源路径不变。

### T7 超大文件拆分（依赖 T1）
- **owned 文件**：`common/util/ImageUtils.java`、`handler/aiChat/BbAiChatHandler.java`（控制流/拼接，**不含回复辅助方法**）、`handler/splatoon/render/SplatoonHtmlRenderer.java`、`SplatoonRecordRenderer.java`。
- **设计要点（分子任务，但作为一个单元整体交付）**：
  - **ImageUtils**：先做**低风险提取**——`getExtension(name)`、`createTranslucentCanvas(...)`（消除 3 处透明初始化）、`joinImages(a,b,fmt,out,boolean horizontal)`（合并横/纵拼接）；类拆分（Cropper/Composer/Renderer/Filter）作为可选后续，保留静态门面兼容调用方。
  - **BbAiChatHandler**：`aiChatHandle` 用 early-return 扁平化配额检查；`persistBotReply` 的 `reduce("",(a,b)->a+b)` 改 `Collectors.joining()`。
  - **Renderer**：颜色/模式映射两份收敛为 `SplatoonStyleConfig`；`battleCard`/`writeOneCoopRecord` 拆 `drawHeader/drawWeapons/drawPlayerBlock`。
- **步骤**：① ImageUtils 提取（保门面）；② BbAiChatHandler 扁平化；③ Renderer 拆分 + StyleConfig。
- **验证**：渲染类**像素/视觉一致实测**（D2 决策口径）；AI 聊天链路实测回复不变。

### T8 魔法值常量化
- **owned 文件**：`connection/qq/QqWebSocketClient.java`（仅 opcode/intents 区域）、`aiAgent/tools/SplatoonRecordTool.java`（仅模式 ID 常量定义）、配置键引用处。
- **设计要点**：
  ```java
  enum QqOpcode { HELLO(10), DISPATCH(0), HEARTBEAT_ACK(11), RECONNECT(7); ... }
  enum QqIntent { GROUP_AT_MESSAGE(1<<25), CHANNEL_AT_MESSAGE(1<<30), DIRECT_MESSAGE(1<<12); ... }
  final class SplatoonModes { static final String TURF = "VnNNb2RlLTE="; ... }
  interface ConfigKeys { String NSO_TYPE = "NSO"; String AUTO_UPLOAD = "autoUploadRecords"; String SESSION_TOKEN = "session_token"; }
  ```
- **步骤**：① 定义枚举/常量；② 全量替换字面量（值必须逐一核对相等）。
- **验证**：编译 + 断言枚举值等于原字面量；QQ 连接握手 intents 与改前一致。

### T9 工具层局部去重/现代写法
- **owned 文件**：`common/util/FileUtils.java`、`RestUtils.java`、`aiAgent/tools/SplatoonRecordTool.java`（仅 `formatDesc`/`battleJudgement`/`coopClear` 等局部方法）。
- **设计要点**：try-with-resources 替换手写 close；`buildJsonHeaders()` 收敛 UA/Content-Type；`battleJudgement` 等改查表或 Optional 链；`formatDesc(parts)` 统一 `(a/b/c)` 拼接。
- **步骤**：① FileUtils/RestUtils 改造 + 现有用例回归；② SplatoonRecordTool 局部方法收敛。
- **验证**：文件读写/HTTP 请求结果不变；解析结果（WIN/LOSE 等）不变。

### T10 BbEventDispatcher 修缮
- **owned 文件**：`dispatcher/BbEventDispatcher.java`。
- **设计要点**：
  - REGEX 规则：把 `Pattern.compile` 移出 per-message 路径，规则注册时预编译并缓存（如 `Map<String,Pattern>`）。
  - `handlerExecute`：日志改 `instance.getClass().getSimpleName() + "." + method.getName()`；区分 `InvocationTargetException` 并记录 `e.getCause()`。
  - 两处匿名 `Runnable` → lambda；可抽 `executeAsync(method, instance, event)` / `executeAsyncIfMatches(...)`。
- **步骤**：① 改正则缓存；② 修日志与异常；③ lambda 化。
- **验证**：构造 MATCH/FUZZY/REGEX 三类规则验证匹配结果不变；故意让某 handler 抛异常，确认日志打印正确类名 + 原始堆栈。

---

## 5. 全局验证与回滚

### 5.1 每单元通用验证
1. **本地编译**（mvn 绝对路径 + JAVA_HOME 17 + 本地仓库），必须通过。
2. **现有单测**相关模块全绿（尤其 T3 的 Provider 测试、T7/T5 的 Splatoon 渲染 demo 测试）。
3. **行为等价类（T2/T3/T5/T6/T7）走活体链路实测**，不能只凭编译通过判定。

### 5.2 提交与回滚
- 每个单元**独立 commit**（commit message 标注单元号，如 `refactor(T3): 抽取 RetryExecutor/HttpErrorClassifier`）。
- 跨波次共享文件严格按顺序、改完即 commit，下游单元基于已 commit 状态开工。
- 任一单元验证不过，`git revert` 该单元 commit 即可，不影响其他单元。
- **不主动 push/merge master**（会触发生产部署），由用户授权后再合。

### 5.3 建议落地顺序汇总
```
波次1: T8 → T9 → T10 → T2 → T1 → T3 → T6   (低风险先行，可穿插)
波次2: T7 (T1 后) , T4 (T8 后)
波次3: T5 (收口，活体实测)
```

---

## 6. 已确认决策

| 编号 | 决策点 | 结论 | 影响单元 |
|---|---|---|---|
| **D1** | T1 回复入口实现方式 | **工具 bean `BbReplies`**（不采用抽象基类继承） | T1（进而 T5/T7 调用统一入口） |
| **D2** | T5/T7 渲染"一致"口径 | **视觉一致 + 实测**（允许像素级微差，不要求字节级一致） | T5, T7 验证 |

> 两项决策已确认，设计据此定稿，可进入实施阶段。
