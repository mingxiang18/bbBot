# 每日资讯日报质量优化 — 调整范围需求

> 阶段：需求 / 范围收敛（基于 05 技术设计，按代码现状核实后裁剪）
> 日期：2026-06-03
> 关系：本文档是对 `05-technical-design-daily-news-quality-optimization.md` 的范围调整版。05 的诊断经代码核实**全部属实**，本文档保留其止血核心，砍掉过度设计，并补上 05 漏掉的生产迁移约束。
> 目标：尽量避免有价值信息被误删，同时避免低价值信息被展示——用最小可维护成本达成。

---

## 0. 与 05 方案的差异（先读这段）

| 维度 | 05 原方案 | 本文档调整 | 理由 |
|---|---|---|---|
| 候选状态模型 | 5 态机 `RAW/ELIGIBLE/SELECTED/REJECTED/DEFERRED` + `quality_score` | 收敛为 `review_state`（仅 `RAW/SELECTED/REJECTED`）+ `first_seen_at`/`last_seen_at` | `DEFERRED`/`ELIGIBLE` 全程无明确转移触发点，单用户 bot 属 YAGNI；漏报真正需要的只是"时间窗候选池"而非全状态机 |
| 质量指标 | 新建 `news_generation_run` 表 | 先用结构化日志行，建表延后/可砍 | 当前规模一条日志足够，查询需求出现再上表 |
| 标题指纹 / RSSHub 钉 digest | Phase 4 | 保留但置于最低优先级，可独立后置 | 低频收益、加复杂度 |
| **DB 迁移落地** | **未提及** | **新增硬约束（见 §2）** | 本项目 push master = 自动上生产，schema 变更顺序错误会整轮 release 失败回滚 |
| 鉴权方式 | token / owner allowlist / 复用 AI Agent，待定 | 明确**复用现有 AI Agent owner 机制** | 与项目已有鉴权一致，不再造 token |

---

## 1. 经核实的现状（事实基线）

| 现状问题 | 代码位置 | 影响 |
|---|---|---|
| AI 返回空/失败 → raw 全量灌水 | `CurateResponse.java:145` 返回 null → `NewsAiCuratorImpl.fallback()` 把**全部** input 以 importance=3 出页 | 低价值条目被整版展示 |
| 超过 `ai.maxItems` 的条目永久漏报 | `dedupAndSave` 存全部 fresh（`news_item` 有 `uk_link_hash` 跨天唯一键）；`curate` 只喂 `interleaveBySource` 截断后的前 `maxItems=100` 条；剩余条目已被标记"见过"，下次不再 fresh | 价值信息永久进不了 AI |
| 同日刷新覆盖旧日报 | `news_daily` 主键 `report_date`，`saveReport` upsert 覆盖；只处理 `fresh` | 二次生成抹掉一次精选 |
| LLM 直接输出 title/link/sourceName | `CurateResponse.Item` 无 ID 回引 | 幻觉/篡改链接可直接进页 |
| canonical 化删光 query 参数 | `LinkHash.normalize()` 只留 `scheme://authority+path` | 以 query 为文章 ID 的源被误并 |
| 串行抓取、connect/read 均 50s | `NewsFetcherImpl` for 循环；`RestClientConfig:26-30` | 单源卡住拖垮整轮 |
| `/news/run` 无鉴权无互斥 | `NewsAdminController` 裸暴露、无锁 | 任意触发、可并发 |
| 无质量指标 | 仅 `news_item`/`news_daily` 两表 | 不可解释、不可诊断 |

数据库：MySQL，**无 Flyway/Liquibase**，`news_item`/`news_daily` 靠手写 `bb-bot-server/src/main/resources/sql/news_ddl.sql` 手动建表。已有幂等迁移范式 `AiAgentSchemaInitializer`（启动时 `CREATE TABLE IF NOT EXISTS` + 幂等 `ALTER TABLE`）。

---

## 2. 硬约束：DB 迁移必须随启动幂等执行 ⚠️

按 `CLAUDE.md`，**push master 即自动构建并部署到生产，无手动卡点**。因此：

1. **任何加列/建表必须做成幂等、随应用启动自动执行**，照抄 `AiAgentSchemaInitializer` 模式新增 `NewsSchemaInitializer`（`ALTER TABLE ... ADD COLUMN` + 列存在则忽略；`CREATE TABLE IF NOT EXISTS`）。
2. **禁止"先 merge 读新列的代码、再手动改库"的顺序**——新 pod 起来读不到列会直接失败，触发 workflow 自动 `rollout undo`，整轮 release 变红。
3. `news_ddl.sql` 同步更新，保留作为全新环境初始化脚本；但生产迁移以 `NewsSchemaInitializer` 为准。
4. 迁移代码必须可重复执行（重启、回滚再上线都不报错）。

**推论：不动 schema 的改动（Phase 1）可安全走 push master 上线；动 schema 的改动（Phase 2+）必须先合入 `NewsSchemaInitializer` 且自测幂等。**

---

## 3. 范围与分期

### Phase 1：安全止血（无 schema 变更，最高优先级）

**目标**：消除"全量灌水"和"覆盖漏报"两个最严重缺陷，可直接 push master 上线。

改动：

1. **空精选合法化**：`CurateResponse.parse` 允许 `items=[]` 作为合法结果返回（而非返回 null）。空结果语义 = 当天无合格资讯。
2. **降级保守化**：LLM 失败 / JSON 解析失败 / 空精选时，**不再 raw 全量出页**。降级顺序：
   - 当天已有成功版本 → 保留旧版本，记降级日志，不覆盖；
   - 无成功版本 → 服务端确定性极小精选（时间窗内、标题非空、链接合法、每源 ≤1-2 条、排除明显低价值关键词、总数上限 8-12），页面显式标注"AI 整理降级"；
   - 仍无内容 → 出"今日暂无足够高价值资讯"轻量页或不出页（见下游决策）。
3. **同日刷新合并**：`generateNow` 同日二次生成时，合并当天已 SELECTED 条目，不允许只用新增条目覆盖旧页。
4. **鉴权**：`/news/run` 复用现有 AI Agent owner 机制；并加进程内互斥锁（同一时间仅一个生成任务）+ 简单限流（如 10 分钟最多一次强制刷新）。

验收：

- AI 返回空数组时不展示 raw 全量。
- LLM 异常时保留当天旧日报。
- 同日第二次生成不删除第一次精选内容。
- 非 owner 请求不能触发；并发触发被串行化/拒绝。

**待决策（阻塞 Phase 1 验收）**：空精选/降级出"暂无"页时，下游 QQ 推送等链路是**不推**、还是**推一条提示**？影响"无内容"分支的实现与验收。

---

### Phase 2：时间窗候选池（最小 schema 变更）

**目标**：把"见过"和"评估过"分开，让超 `maxItems`、故障期、被误删的条目仍能重新进入评估。**仅引入达成此目标所需的最小字段**。

改动：

1. 新增 `NewsSchemaInitializer`（见 §2），为 `news_item` 幂等加列：
   - `first_seen_at DATETIME`（首次采集时间，兼当时间窗依据之一）
   - `last_seen_at DATETIME`（最近一次仍在源 feed 出现，`dedupAndSave` 命中已存在条目时更新）
   - `published_at DATETIME NULL`（标准化发布时间，解析失败为空）
   - `review_state VARCHAR(16)`（仅 `RAW / SELECTED / REJECTED`，默认 `RAW`）
   - `reject_reason VARCHAR(32) NULL`
2. `dedupAndSave` 改为 upsert raw：新条目插入、已存在条目更新 `last_seen_at`（不再因 `uk_link_hash` 把旧条目永久判死）。
3. 新增 `listEligibleForReport(date)`：返回**时间窗内** `review_state IN (RAW)` + 当天 `SELECTED` 的候选池，剔除明确过期/拒绝。
4. 调度改为对候选池 `curate`，而非仅 `fresh`。
5. 默认时间窗（可按源配置覆盖）：中文综合/财经/AI/科技/数码 36h；游戏/汽车等慢源 72h；`published_at` 缺失按 `first_seen_at` 计，每源保留 ≤2 条配额。

验收：

- 超过 `ai.maxItems` 的条目在下一次仍可被评估。
- 故障恢复后时间窗内条目仍能重新进入候选。
- 候选/精选/拒绝数量可从 DB 查询。
- `NewsSchemaInitializer` 在空库、已迁移库、重启场景均幂等不报错。

> 注：05 的 `ELIGIBLE`/`DEFERRED`/`quality_score` 暂不引入。若后续出现明确的"暂缓再评估"业务流，再在此基础上扩展状态，避免一次性引入无转移逻辑的死状态。

---

### Phase 3：AI ID 化与服务端回填

**目标**：消除幻觉链接与 prompt injection 风险。

改动：

1. prompt 输入增加 `id / publishedAt`；LLM 输出仅返回 `id / clusterIds / category / summaryZh / importance / note`，**不再返回 title/link/sourceName**。
2. 服务端按 `id` 回填真实 title/link/sourceName/english。
3. 校验：未知 id、重复 id、非法分类、空摘要、超长摘要 → 修正或拒绝。
4. 保留 `clusterIds` 供合并追溯。

验收：

- LLM 输出不存在的链接不会进入页面。
- LLM 无法凭空创建新闻。
- 合并条目保留 `clusterIds`。

---

### Phase 4：源可靠性与可观测（最低优先级，可拆分后置）

**目标**：可持续维护源质量。各项相互独立，可按收益单独排期。

改动（按建议优先级）：

1. **抓取并发 + 真实超时**（收益最高）：有限并发 4-6；单源 connect 3-5s、read/response 10-15s；修正 `rest.readTimeout` 语义确保是响应读取超时而非仅 connection-request；单源失败隔离不变。
2. **canonical 白名单**：默认只删追踪参 `utm_*/spm/from/ref/source/fbclid/gclid`，保留 `id/articleId/contentId/p/post` 等业务参，按源可配置。先搭框架、默认保守，名单按源经验增量补。
3. **质量指标（先日志后表）**：每次生成记录结构化日志行（`fetched_count/eligible_count/selected_count/rejected_count/source_success/source_failed/ai_status/publish_status`）。确有查询需求再升级为 `news_generation_run` 表。
4. **RSSHub 固定版本**：`diygod/rsshub:latest` 改钉 tag/digest，发布前健康探测（HTTP 200 / item>0 / 最新时间不早于阈值 / 非错误页）。
5. **标题指纹辅助**（最低优先级，可砍）：`title_fingerprint` 仅作服务端误删防护提示，不替代 AI 聚类。
6. `/news/run` 增加 `dryRun=true`（只返回统计不发布）与 `forceRebuild=true`（从候选池重建当天日报、不重抓）。

验收：

- 单源卡住不拖垮整轮（并发 + 真实超时生效）。
- 业务 ID 在 query 中不被错误剥离。
- 可看到每源最近成功时间/条数/最新发布时间。
- 可 dryRun 预览质量。

---

## 4. 测试补充（沿用 05 §9，按本范围对齐）

1. `items=[]` 不触发 raw 全量 fallback。（P1）
2. LLM 异常时保留当天旧日报。（P1）
3. 同日二次生成不覆盖旧精选。（P1）
4. `/news/run` 未授权 / 并发触发 / 限流行为。（P1）
5. 超过 `maxItems` 的候选在下一次仍可评估。（P2）
6. `NewsSchemaInitializer` 在空库/已迁移库/重启下幂等。（P2）
7. `published_at` 时间窗过滤覆盖中英文常见 RSS 日期格式。（P2）
8. LLM 返回未知 id / 伪造链接被拒绝。（P3）
9. query 中业务 ID 不被错误剥离。（P4）

---

## 5. 推荐执行顺序

1. **Phase 1 立即做**：无 schema 风险、止住两个最严重缺陷、可直接 push master。先确认 §3 的下游推送决策。
2. **Phase 2 紧随**：但必须先落 `NewsSchemaInitializer` 并自测幂等，再合读新列的代码。
3. **Phase 3 / Phase 4 按收益排期**，Phase 4 内部各项可独立提交；标题指纹、`news_generation_run` 建表为可选项。

落地 Phase 1+2 后，质量风险从"可能永久漏报或全量灌水"降到"可重跑、可解释、可人工继续调优"。

---

## 6. 审批点

请确认后进入实现：

1. 接受"宁缺毋滥"：AI 失败/空精选时默认不 raw 全量出页。
2. 候选状态模型收敛为 `RAW/SELECTED/REJECTED` + 时间窗（不引入 `DEFERRED/ELIGIBLE/quality_score`）。
3. `/news/run` 鉴权复用现有 AI Agent owner 机制 + 进程内互斥 + 限流。
4. schema 变更一律走幂等 `NewsSchemaInitializer` 随启动执行（不手动改生产库）。
5. 质量指标先用结构化日志，`news_generation_run` 表延后/按需。
6. 空精选/降级"无内容"时，下游 QQ 推送是不推还是推提示？（阻塞 Phase 1 验收）
7. 默认时间窗 36h / 慢源 72h 是否可接受。
8. 是否按 Phase 1→2→3→4 顺序逐步提交，先止血再重构。
