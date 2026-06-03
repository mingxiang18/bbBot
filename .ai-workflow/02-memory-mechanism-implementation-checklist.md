# bbBot 记忆机制重构 — 落地清单

> 本清单是 `02-memory-mechanism-redesign-plan.md` 的可执行版本，已把方案评审 + Claude Code 记忆机制文章的对照学习内化进来。
> 设计动机/原理见原方案；本文件只列**要做什么、改哪里、做完怎么算过**。
> 主线不变：不上向量库，走「结构化卡片 + 常驻索引 + 按需加载 + 轻模型兜底选择 + 时间老化」。
>
> **bbBot 是两层记忆**（对齐 CC 的 static/dynamic 划分）：
> - 静态层 = `prompts.yml`（鱿鱼人设/声明式规则），启动注入；
> - 动态层 = `ai_memory_item`（从互动学到的卡片），按需检索注入。
> 两层边界规则：**动态层不抽取已在静态层里的事实**（详见「与旧机制的取舍」）。

## 现状基线（已核对代码，作为改造起点）

| 组件 | 文件 | 当前行为 |
|---|---|---|
| 事件流/会话/事实三表 | `sql/ai_agent_schema.sql` L83-134 | `ai_memory_event` / `ai_memory_session` / `ai_memory_fact` 已落地 |
| 会话切分 | `aiAgent/memory/SessionTracker.java` L44-102 | 用 `started_at + sessionGapMinutes(30)` 切，**无 `last_event_at`**，活跃聊天会被误切 |
| 记忆编译 | `aiAgent/memory/MemoryCompiler.java` L36-330 | 四段(today/week/longterm/facts)拼成 memory.md，**整包注入**；截断**按字符数**(默认 6000) |
| 事实库 | `aiAgent/memory/FactStore.java` | ngram FULLTEXT + LIKE fallback + tag 命中排序；**无 decay/importance/status/confidence** |
| 经验库 | `aiAgent/memory/ExperienceStore.java` | **文件系统** category `.md`，非数据库 |
| 注入入口 | `handler/aiChat/BbAiChatHandler.java` L506-533 `composePersonality` | memory.md 整包拼进 personality |
| 记忆 prompt | `resources/prompts.yml` L7-9 clue-suffix | 强制「指明曾经讨论/曾经出现」，不自然 |
| 事件记录 | `aiAgent/memory/MemoryEventRecorder.java` + `dispatcher/BbEventListener.java` | 入口最前 recordInbound 自动分类落库 |

> ⚠️ 文件路径以实际仓库为准（核查时部分在另一 worktree 拷贝下，类名/行号一致）。

---

## ⭐ 贯穿全程的硬约束（每个 Phase 都要守）

1. **记忆注入不得拖慢主回复。** QQ 在回复 >5s 时会重推 → bot 发两张图（已发生过的事故，见记忆 `qq_duplicate_reply`）。
   - 任何在主 LLM 之前的同步调用（尤其 selector）必须有**硬超时 + 失败 fallback**，超时即退化为「按 importance/last_seen 取 topK」，**绝不阻塞回复**。
2. **每改一处先 commit 再验证**（工作分支），保持可回溯提交点。
3. **改完主动本地编译验证**：`mvn` 用绝对路径 + JAVA_HOME17 + 本地仓库参数（见记忆 `build_environment`）。
4. **push master = 自动部署生产**，每个 Phase 合并前确认 release.yml 能跑绿。
5. 外部链路（DB schema、LLM 调用）改动要**打活体链路实测**，别只「编译过 + 组件健康」就声称能用。

---

## Phase 1：基础修正（低风险高收益，不改用户可见结构）

目标：修会话边界 + prompt 自然度 + 截断口径，并**前置验证 groupId 链路**（给 Phase 2/3 的 scope 铺路）。

- [x] `ai_memory_session` 增加 `last_event_at`（`AiAgentSchemaInitializer` 建表带列 + 幂等 ALTER + 一次性回填；entity 加字段）。
- [x] `SessionTracker.attachSessionId`：reuse 改按 `last_event_at + gap > now`（null 回退 started_at），每次复用/新建都写 `last_event_at = now`。
- [x] `SessionTracker.sweepInactiveSessions`：扫描条件改为 `last_event_at < now - gap`。
- [x] `MemoryCompiler.assemble` 截断：新增字节上限（默认 25KB，二分定位 char 边界不切坏多字节）+ 明确截断提醒。
- [x] `prompts.yml` clue-suffix + `BbAiChatHandler` 记忆注入（新增 `MEMORY_USAGE_GUIDANCE`）：改为自然使用、历史追问才说明来源。
- [x] **前置核查（group scope 首期开启的硬门槛）— 通过 ✅**：核查 OneBot/QQ群/QQ频道/Telegram/Discord 全平台，群聊均稳定带非空 `groupId` + 群内真实发送者 `userId`（`BbReceiveMessage` 透传 → `recordInbound` → `SessionTracker`/`BbAiChatHandler` 全程不被覆盖）。**group scope 可首期上线。**
  - 顺带修掉一个相邻 bug：私聊（`groupId` 空）查最近 session 时 `eq(isNotBlank(groupId),…)` 不加约束，会复用到同一用户最近的【群】session → 私聊↔群串味。已在 `SessionTracker`(attachSessionId/forceEndCurrent) + `MemoryQueryService` 用 `isNull(isBlank(groupId), groupId)` 修正（注意不能用 `eq(null)`，MyBatis-Plus 会生成 `=NULL` 永假）。

验收（已本地活体实测 @2026-06-04，mysql:8+mock LLM via bb-dev harness）：
- [x] **session 复用经实测**：多条私聊复用同一 session、message_count 累加、`last_event_at` 持续推进；私聊与群是独立 session（串味 bug 不复现）。
- [ ] ⏳ 普通回复不再机械「曾经讨论」：prompt 已改，但本地 clue 路径因测试库缺 `ai_keyword` 表报错，未观测到回复文本（与本次改动无关）。
- [ ] ⏳ memory.md 字节截断：逻辑+编译过，留 Phase 5 单测。
- [x] groupId 链路结论记录在案（已通过）。

---

## Phase 2：结构化记忆卡片（新表并行写，旧表不动）

目标：新增 `ai_memory_item`，后台抽卡片落库，**user + group scope 同时上**（决策 2；前提是 Phase 1 groupId 链路核查通过）。

### 数据模型
- [x] 新表 `ai_memory_item`（`AiAgentSchemaInitializer` DDL）：含 type/scope/user_id/group_id/subject_user_id/summary/body/why/how_to_apply/evidence/tags/search_text/status/confidence/importance/expires_at/last_seen_at/superseded_by/source_session_id/created_at/updated_at。
- [x] 索引：`idx_scope_user_group`、`idx_type_status`、`idx_updated`、`idx_expires(status,expires_at)`、`FULLTEXT ft_memory_search(search_text) WITH PARSER ngram`。
- [x] **类型「4 骨架 + scope 承载群聊差异」**：`MemoryType` = user_profile/preference/project_state/reference + inside_joke/ephemeral_event（共 6，群内关系/话题靠 scope 表达，不再开 relationship/group_topic）。
- [x] 枚举 `MemoryType`(含宽松 parse) / `MemoryScope`(needsUser/needsGroup) / `MemoryStatus`；entity + mapper + service(impl)。
- [x] **`summary` 检索命脉**：抽取 prompt 强约束「一句话、自带主语、可独立理解」。

### 抽取与策略
- [x] `MemoryExtractor`：从 ended session + 本 session 对话 + **已有记忆索引**抽候选。
  - [x] **复用 `compileSessionSummary` 同一次 LLM 调用**：append `buildCardPromptSection`，回复里解析 ` ```json {cards:[…]} `（离线实测通过），不另起模型。
  - [x] 抽取规则全部进 prompt：只抽影响未来回复的、相对日期→绝对、preference/project_state 必填 why+howToApply、低置信度丢弃、私聊禁 group/user_in_group scope。
  - [x] **`project_state` 默认 14 天过期**（`projectStateTtlDays`，候选给了 `expiresInDays` 则优先）。
  - [x] **写入禁令**进 prompt：不抽静态人设已有/可查到/单轮临时态。
- [x] `MemoryPolicy`：**刻意做轻**——insert / refresh(同义卡更 last_seen) / supersede(仅 LLM 明确给 supersedesKey) / ignore；不做 LLM 语义冲突判定。
  - [x] `supersede` 软降级旧卡为 `superseded` + 回填 `superseded_by`，不硬 delete；置信度<0.6 ignore；scope 越权降级；preference/project_state 缺 why/howToApply 直接 ignore。
- [x] `MemoryLifecycleSweeper`：复用 `@Scheduled`，唯一 status 归属人，定时把 `expires_at<now` / `last_seen_at` 超龄(默认60天) 的 active→stale。

验收（已本地活体实测 @2026-06-04）：
- [x] **端到端实测通过**：force-end session → sweep 跑蒸馏 → 同一次 LLM 调用产出卡片 → `MemoryPolicy [apply] 候选=1 insert=1` → `ai_memory_item` 落 preference/user/conf=0.95 卡，source_session 匹配。
- [ ] ⏳ 流水账不进 active：依赖真 LLM 遵守写入禁令（mock 是 canned，未覆盖），留真 LLM 观察。
- [x] 相对日期转绝对 + `expiresInDays`→`expires_at`（parse 离线实测）。
- [x] **LLM 调用未翻倍经实测**：日志同一 session 既「抽出 1 条 facts」又出 1 张卡片，同一次 `chat(LIGHT)`。

---

## Phase 3：索引常驻 + 内容按需（核心，延迟敏感）

目标：把整包 memory.md 注入换成「短索引常驻 + 少量正文按需」。

- [x] **短索引**：由 `MemorySelector` 从 `ai_memory_item` 直接产出（每条 `key [type/scope] summary`）。
  - 偏离说明：未复用 `ensureCompiledMemory` 缓存——卡片是 DB 实时查（已索引、cheap、永远新鲜），无需文件缓存层；候选上限 `selectCandidateCap=80` 控量。
- [x] **`MemorySelector`（分层，热路径优化的关键）**：
  ```
  0. alreadySurfaced 过滤: 本会话上一轮已注入过的卡片这轮排除，名额留给新候选(学 CC)
  1. 结构化过滤: scope(user/global/group/user_in_group，强校验 groupId) + status in (active,stale) + type 按消息意图
  2. 文本粗筛: ngram FULLTEXT → LIKE fallback → tag 命中
  3. 候选数 ≤ N(=8)?      → 是: 直接全部注入，【不调模型】       ← 覆盖绝大多数轮，解决延迟
                         → 否: 才调【最高档模型】做选择题(最多 8 条，超时 1.5s)
  4. 注入正文: 带 type/scope/age/status，对 stale/project_state/reference 加老化提醒
  ```
  - **门控负责「少调」、模型选型负责「调得准」，两者不是二选一**（学 CC：CC 用 Sonnet 不用 Haiku，因为选错一条记忆会污染整条回复，判错代价 ≫ 省的 token）。→ selector 一旦触发，**用够准的那档模型，不要图便宜**；靠候选阈值把它变稀有来控成本/延迟。
  - **模型调用必须有硬超时；超时/异常 → fallback 按 importance+last_seen 取 topK，不阻塞主回复。**
  - selector prompt 措辞偏严：**「只选你确信对当前回复有帮助的；宁可少选不可错选」**（已落 `MemorySelector.llmSelect`）。
- [x] 渲染（assembler 逻辑并入 `MemorySelector.render`）：`--- Memory Index ---` + `--- Selected Memories ---` + 记忆使用规则。
  - [x] **老化警告**：今天/昨天不警告；≥2 天的 project_state/ephemeral_event/reference 加「N 天前、动手前先核实」；user_profile/preference 放宽（离线实测通过）。
  - [x] **用时验证规则** + scope 不匹配不得用 + 自然使用不报来源：写进 `USAGE_RULES`。
  - 说明：未用 `<system-reminder>` 标签包裹（bbBot 注入在 system prompt 的 personality 段内，已是系统语境；用 `--- Selected Memories ---` 分隔，不会被当成用户话）。
- [x] `BbAiChatHandler.composePersonality`：改为 selector 短索引+选中正文；**卡片未积累时回退旧 memory.md**（过渡不空窗）。
- [x] `ai_memory_selection_log` 表 + `MemorySelectionLogger`(best-effort) + Sweeper TTL 清理(14天)。

验收（已本地活体实测 @2026-06-04，从 mock 收到的真实 system prompt 直接观测注入块）：
- [x] **scope 隔离经实测**：私聊只注入 user/global 卡，群卡 `mv_group1`/`mv_uig1` **确认未出现在私聊**（离线穷举 + live 双证）。
- [x] **老化警告经实测**：stale/6天前 project_state 注入时带「N 天前、动手前先核实」。
- [x] **selector 热路径稳定经实测**：私聊多轮 0 warn/0 超时/0 阻塞，bot 正常回复；候选≤8 走直接注入快路径。
- [x] alreadySurfaced 去重逻辑就位。
- [ ] ⏳ 延迟 P95 压测 + >8 候选触发模型选择/超时 fallback：未做负载测试（mock 下 CHAT 选择走 fallback 路径，逻辑已验）。

---

## Phase 4：用户可控与管理

- [ ] 自然语言意图：「记住…」立即写 preference；「这个别记」当轮不进候选；「忘掉…这条」标 deleted；「你记得我什么？」展示可读索引；「这条记错了」走纠错→旧卡 superseded/deleted。
- [ ] **普通用户可查看自己的记忆**（决策 1）：`你记得我什么?` 对普通用户开放、只读**本人** scope 的卡片，按 userId 强校验、零跨用户泄漏。删除/纠错动作加一步确认。
- [ ] 扩展 owner 命令：`/aiAgent.memory.view|search|item|delete|supersede|rebuild|debug`（debug：模拟某条消息会选中哪些卡片）。owner 命令可跨用户/群；普通用户入口只触及本人记忆。

验收：
- [ ] 用户能查看记住了什么、能删错误记忆；纠正后旧卡不再 active。

---

## Phase 5：评估与调参

- [ ] 单测覆盖**管道**（字段落库、scope 过滤、selector 分层分支、超时 fallback、index 截断）。
- [ ] **人工评估样例集**覆盖抽取质量（单测测不了 LLM 抽得对不对，预期摆正）。
- [ ] prompt snapshot 测试。

验收：
- [ ] 偏好不被临时玩笑误记；群梗不跨群；旧 project_state 带 stale 提示；普通聊天自然使用记忆。

---

## 与旧机制的取舍（已定）

- **静态/动态两层边界（学 CC）**：`prompts.yml`=静态声明式人设；`ai_memory_item`=动态学习式卡片。动态层**不得重复抽取已在静态层里的事实**，否则记忆与人设会打架（已落到 Phase 2 抽取边界）。
- 保留：`ai_memory_event`（审计）、`ai_memory_session`（抽取边界）、ngram FULLTEXT、`search_memory` 工具、memory-workspace 目录。
- **不学的**：CC 的 6 级静态层 + `@include` + paths-glob 条件规则，是给多项目编程 agent 的；bbBot 单服务、且 `scope` 已覆盖「按上下文条件注入」，不引入。
- **`ai_memory_fact` 历史数据不自动迁移**到 `ai_memory_item`（旧数据无结构、质量参差，搬过去是搬垃圾）；新系统上线后自然积累，旧表只读兼容、自然淘汰。
- `ExperienceStore` 降级为手动笔记工具，长期记忆主路径交给 `ai_memory_item`。
- importance/confidence 是 LLM 给的浮点，**噪音大**：只做粗分桶（高/中/低）用于排序/截断，不设精细阈值。

---

## 已定决策（实现按此走）

1. **普通用户可查看自己的记忆**（只读自己的；删除/替代仍建议加确认，跨用户隔离按 userId 强校验）。
2. **group 记忆首期开启**（不再"先只 user"）。⚠️ **前置硬门槛**：Phase 1 必须先确认 `groupId` + 群内 `userId` 链路可靠；若不可靠，这是 **blocker**，需回报后再定，不可带病上 group scope。
3. **`project_state` 默认过期 14 天**（`expires_at = created_at + 14d`）。
4. **selector 触发时用最高档模型**（学 CC：相关性判错代价 ≫ token；靠候选阈值把调用变稀有来控成本/延迟）。

### 我替你定的默认（可改）

5. selector 候选阈值 **N = 8**、模型调用**超时 1.5s**（超时即 fallback 按 importance+last_seen 取 topK，不阻塞回复）。
6. `ai_memory_selection_log` **首期上线**，**TTL 14 天**，定时清理。

---

## 推荐落地顺序

Phase 1 → 2 → 3 为最小可用闭环：先修边界与自然度，再并行写结构化卡片，最后把整包注入换成短索引 + 按需正文。Phase 3 的**分层 selector + 超时兜底**是全案唯一会引发线上事故的点，务必先做对再上。
