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

- [ ] `ai_memory_session` 增加 `last_event_at` 字段（DDL + entity + mapper）。
- [ ] `SessionTracker.attachSessionId`：reuse 条件从 `started_at + gap > now` 改为 `last_event_at + gap > now`；每次 record 更新 `last_event_at`。
- [ ] `SessionTracker.sweepInactiveSessions`：扫描条件改为 `last_event_at < now - gap`。
- [ ] `MemoryCompiler.assemble` 截断：在字符数之外**增加字节上限**，截断时写明确的「...(truncated)」提醒（保持现有优先级 today>facts>week>longterm）。
- [ ] `prompts.yml`：记忆使用措辞改为「自然使用长期记忆，不主动声明来源；仅当用户追问过去/是否记得/记忆可能过期时才说明来源、时间、不确定性」。
- [x] **前置核查（group scope 首期开启的硬门槛）— 通过 ✅**：核查 OneBot/QQ群/QQ频道/Telegram/Discord 全平台，群聊均稳定带非空 `groupId` + 群内真实发送者 `userId`（`BbReceiveMessage` 透传 → `recordInbound` → `SessionTracker`/`BbAiChatHandler` 全程不被覆盖）。**group scope 可首期上线。**
  - 顺带修掉一个相邻 bug：私聊（`groupId` 空）查最近 session 时 `eq(isNotBlank(groupId),…)` 不加约束，会复用到同一用户最近的【群】session → 私聊↔群串味。已在 `SessionTracker`(attachSessionId/forceEndCurrent) + `MemoryQueryService` 用 `isNull(isBlank(groupId), groupId)` 修正（注意不能用 `eq(null)`，MyBatis-Plus 会生成 `=NULL` 永假）。

验收：
- [ ] 连续聊天 >30min 中途不断开 → 不被切成多个 session（活体验证）。
- [ ] 普通回复不再机械输出「曾经讨论过」。
- [ ] memory.md 超长时按字节正确截断且带提醒。
- [ ] groupId 链路结论记录在案。

---

## Phase 2：结构化记忆卡片（新表并行写，旧表不动）

目标：新增 `ai_memory_item`，后台抽卡片落库，**user + group scope 同时上**（决策 2；前提是 Phase 1 groupId 链路核查通过）。

### 数据模型
- [ ] 新表 `ai_memory_item`（字段见原方案「推荐数据模型」）：含 type/scope/user_id/group_id/subject_user_id/summary/body/why/how_to_apply/evidence/tags/search_text/status/confidence/importance/expires_at/last_seen_at/superseded_by/source_session_id/created_at/updated_at。
- [ ] 索引：`idx_scope_user_group`、`idx_type_status`、`idx_updated`、`FULLTEXT ft_memory_search(search_text) WITH PARSER ngram`。
- [ ] **类型用「4 类骨架 + scope 承载群聊差异」，不开 8 套并行 type**（学 CC：CC 全系统只 4 类 `user/feedback/project/reference`）：
  - 骨架 4 类：`user_profile`(画像) / `preference`(偏好，对应 CC 的 feedback) / `project_state` / `reference`。
  - **群聊差异交给 `scope`（user/group/user_in_group），而不是再开 type**——原方案的 `relationship`/`group_topic` 其实是「带 scope 的画像/话题」，用 scope 表达即可。
  - 仅保留 CC 没有、群聊确实独有的少数：`inside_joke`、`ephemeral_event`。
  - 收益：类型从 8→约 6，每类一套老化/校验逻辑的策略面和测试面显著收窄。
- [ ] 枚举 `MemoryType` / `MemoryScope` / `MemoryStatus`；entity + mapper + service。
- [ ] **`summary` 是检索命脉**（对应 CC frontmatter 的 `description`：selector 只看它决定选不选）。抽取时强约束 summary 写成「一句话、自带主语、可独立理解」，不是流水账标题。

### 抽取与策略
- [ ] `MemoryExtractor`：在 session ended 后从 `ai_memory_session` + 本 session `ai_memory_event` + **当前用户已有记忆索引**抽结构化候选 JSON。
  - **⚠️ 成本约束：复用已编译/已缓存的会话上下文，和现有 `compileSessionSummary` 的 LLM 调用合并为一次** —— 一次调用同时产出 summary 和候选卡片，不为「抽记忆」重建上下文（学 CC 的 forked-agent + 复用 prompt cache 思路；bbBot 是 session 结束批处理，做不到 live fork，但「不重建上下文」的原则照用）。
  - 抽取规则：只抽「会影响未来回复」的；不抽流水账（除非用户显式要记）；相对日期→绝对日期；preference/project_state 必须有 why + how_to_apply；低置信度只记候选不进 active。
  - **`project_state` 默认 `expires_at = created_at + 14 天`**（决策 3；用户给了明确期限则用用户的）。
  - **写入边界（学 CC 的禁令清单）**：不抽 ① 已在 `prompts.yml` 静态人设里的事实；② 能从代码/配置/数据源（splatoon/news 等）直接查到的内容；③ 单轮对话上下文/临时任务态。原则：**只记代码和静态层都推不出来的东西**。
- [ ] `MemoryPolicy`：**刻意做轻**（学 CC：CC 不维护语义冲突检测，靠「老化警告 + 用时验证」防过时误导）。
  - 主路径只做 `insert / merge(明显同主题归并) / refresh(更 last_seen) / ignore`。
  - `supersede` **只处理「明显同主题的新覆旧」**：把旧卡降级 `stale` 并记 `superseded_by`，**不硬 delete**、**不追求 LLM 语义冲突判定**（那是易错又费模型的活）。
  - 省下的可靠性预算压到 Phase 3 的注入端：staleness 警告 + 用时验证（见下）。
- [ ] `MemoryLifecycleSweeper`：**复用现有 `@Scheduled` 基础设施**（参考 `sweepInactiveSessions`），定时把 `expires_at < now` / `last_seen_at` 超龄的卡片降级 active→stale。明确 status 流转的唯一归属人就是它。

验收：
- [ ] 含明确偏好的对话结束后能生成 `preference` 卡片，带 why/how_to_apply。
- [ ] 流水账不进 active。
- [ ] 相对日期转成绝对日期。
- [ ] session 结束的 LLM 调用次数没有因新增 Extractor 而翻倍（用日志/计数确认）。

---

## Phase 3：索引常驻 + 内容按需（核心，延迟敏感）

目标：把整包 memory.md 注入换成「短索引常驻 + 少量正文按需」。

- [ ] **`MemoryIndexCompiler` 不另起新类**：改造现有 `MemoryCompiler.ensureCompiledMemory`，让它产出**短索引**（每条一行：`id type scope: summary`），复用其缓存与失效逻辑。
  - 限制：≤200 行 + ≤25KB 双限制；超限按 importance/status/updated_at/scope 保留高价值项并写截断提醒。
- [ ] **`MemorySelector`（分层，热路径优化的关键）**：
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
  - selector prompt 措辞偏严：**「只选你确信对当前回复有帮助的；宁可少选不可错选」**（照搬 CC 的 selective/discerning 取向）。
- [ ] `MemoryPromptAssembler`：组装 `--- Memory Index ---` + `--- Selected Memories ---` + 记忆使用规则。
  - 选中正文用 `<system-reminder>` 包裹注入，避免被当成「用户刚说的话」（学 CC）。
  - **老化警告**：今天/昨天不警告；≥2 天的 `project_state`/`ephemeral_event`/`reference` 主动加「这条存于 N 天前，可能已变，动手前先核实」；`user_profile`/`preference` 这类稳定项放宽阈值（按 type 老化，已在原方案）。
  - **用时验证规则**（学 CC 的 "Before recommending from memory"）：记忆里提到的具体事实（某 API/某决定/某文件/某 flag）在据此行动前，先对照当前上下文/数据源确认，「记忆说 X 存在」≠「X 现在存在」。
  - 使用纪律：自然使用不主动声明来源；scope 不匹配不得用；过期项谨慎。
- [ ] `BbAiChatHandler.composePersonality`：从「整包 memory.md」改为「短索引 + 选中正文」注入。
- [ ] `ai_memory_selection_log` 表（可选但建议）：记 candidate_keys / selected_keys / selector_model。**必须带 TTL（保留 7-14 天）**，定时清理，防膨胀。

验收：
- [ ] prompt 注入长度稳定，不随历史无限增长。
- [ ] 同一问题选中相关卡片，而非全部长期事实。
- [ ] 连续多轮对话中，同几张卡不会每轮重复注入（alreadySurfaced 生效）。
- [ ] **注入记忆带来的额外延迟 P95 < 目标值；selector 故障不阻塞回复**（活体验证，重点防 QQ 重复回复复发）。
- [ ] stale 的 project_state 注入时带「N 天前、动手前先核实」警告。
- [ ] 群记忆不出现在私聊/其他群（group scope 首期即上，重点验证）。

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
