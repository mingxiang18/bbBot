# 星露谷攻略助手实现结论

> 阶段：Implementation progress
> 日期：2026-06-26
> 分支：`master`

## 当前结论

星露谷攻略助手已经形成可发布的第一版：`/星露谷` 命令和 `stardew_guide` AI tool 共用同一条链路，由 AI 先做问题分类和关键词抽取，再按类型检索结构化证据，最后整合成自然语言回复。用户侧不展示数据版本、校验日期、Wiki、本地库等内部实现信息。

这版的目标不是把本地数据做成完整百科，而是覆盖高频玩法问题，并通过官方 Wiki 兜底降低临时问题答不上或答偏的概率。后续扩展应继续围绕“类型化检索 + 证据整合 + 防串路测试”推进，避免回到命中关键词堆规则的模式。

## 机制 Review 结论

当前职责边界保持为：`StardewQueryPlannerService` 负责 AI 分类、关键词抽取和高置信防串路纠偏；`StardewGuideRetriever` 只按 typed plan 组装检索 query 并取证据；`StardewGuideService.answerEvidence` 只提供 typed evidence API；`StardewGuideAssistantService` 只根据证据生成自然回复。旧 `answer(String)` 仅保留兼容，不作为正常 `/星露谷` 和 AI tool 主链路。

当前机制判断：方向合理，职责基本清晰。AI 负责理解用户意图和生成检索计划，本地代码负责少量高置信纠偏、确定性证据检索和安全兜底，最终回答再交给 AI 做表达整合。后续优化不应继续扩大旧自由文本路由，也不应让 retriever 自己猜类型；如果新增“制作配方、节日、农场布局”等类目，应先扩 typed intent 和结构化证据，再补 planner 分类规则和防串路测试。

本轮新增 planner 后处理：AI 返回具体类型后，如果命中“特别订单 vs 传说鱼/鱼塘”“鱼塘产物 vs 鱼塘建筑”“通用攻略 vs 技能/特别订单/鱼塘/怪物掉落”等高风险边界，使用本地高置信规则纠偏；但不会把 `UNKNOWN` 或明确 typed miss 强行改写成旧自由文本路由，避免问 A 答 B。

技能类目已从通用攻略拆出 `skillGuides` 专用 typed 数据。`SKILL` intent 现在优先返回 `skill_guide` / `skill_guide_list` evidence，通用 `GUIDE` 仍保留精通、锻造、博物馆等攻略兜底。

## 已覆盖范围

已结构化覆盖鱼类、收集包、居民、作物、建筑/工具、机器/制作、商店/兑换、资源、怪物、鱼塘、料理/书籍、特别订单/齐先生任务、技能、通用攻略等高频类目。

当前数据量级：鱼类 74 条，收集包 58 个，居民 34 位，作物 30 个，建筑 27 个，工具 6 类，机器/制作 80 个，商店/兑换 29 个，资源 181 项，怪物 58 个，鱼塘 73 条，料理/饮品 83 道，书籍 26 本，特别订单 28 个，技能攻略 9 个，通用攻略 39 项。

居民已完成可送礼居民的首轮可发布覆盖：生日、礼物偏好、普通日程和高频雨天/星期/诊所/上课规则。更复杂的节日、姜岛度假、婚后、好感剧情、绿雨、沙漠节等放入后续增强。

## 验证结论

最近一次星露谷聚焦单元测试通过：`207 tests, 0 failures`。

最近一次 `bb-bot-server` reactor compile 通过：`BUILD SUCCESS`。

JSON 轻量校验通过：`resources=181`，`guides=39`，`specialOrders=28`，`skillGuides=9`；重复 resource id、special order id 和 skill guide id 均为空。`git diff --check` 对本轮相关文件通过。

实际服务启动测试当前按目标暂停；历史本地服务测试中，非星露谷链路仍有既有 Splatoon3 A13 专用工具路由失败，不影响本功能发布判断。

## 后续原则

后续优先补齐姜岛/火山材料、建筑、机器、工具、制作、商店和居民复杂规则的偏门数据。每轮补数都要同步补 typed intent、防串路单元测试、JSON 校验、聚焦单元测试、reactor compile 和 `git diff --check`。

测试重心也应逐步迁移：新增类目优先覆盖 `StardewQueryPlannerServiceTest`、`StardewGuideRetrieverTest`、typed `StardewGuideService.answerEvidence` 和 `StardewGuideAssistantServiceTest`；旧 `answer(String)` 测试只保留存量兼容和少量关键回归，避免为了旧路由继续堆命中规则。
