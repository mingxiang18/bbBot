# 星露谷攻略助手实现结论

> 阶段：Implementation progress
> 日期：2026-06-26
> 分支：`master`

## 当前结论

星露谷攻略助手已经形成可发布的第一版：`/星露谷` 命令和 `stardew_guide` AI tool 共用同一条链路，由 AI 先做问题分类和关键词抽取，再按类型检索结构化证据，最后整合成自然语言回复。用户侧不展示数据版本、校验日期、Wiki、本地库等内部实现信息。

这版的目标不是把本地数据做成完整百科，而是覆盖高频玩法问题，并通过官方 Wiki 兜底降低临时问题答不上或答偏的概率。后续扩展应继续围绕“类型化检索 + 证据整合 + 防串路测试”推进，避免回到命中关键词堆规则的模式。

## 机制 Review 结论

当前职责边界保持为：`StardewQueryPlannerService` 负责 AI 分类、关键词抽取和高置信防串路纠偏；`StardewGuideRetriever` 只按 typed plan 组装检索 query 并取证据；`StardewGuideService.answerEvidence` 只提供 typed evidence API；`StardewGuideAssistantService` 只根据证据生成自然回复。旧 `answer(String)` 仅保留兼容，不作为正常 `/星露谷` 和 AI tool 主链路。

当前机制判断：方向合理，职责基本清晰。AI 负责理解用户意图和生成检索计划，本地代码负责少量高置信纠偏、确定性证据检索和安全兜底，最终回答再交给 AI 做表达整合。后续优化不应继续扩大旧自由文本路由，也不应让 retriever 自己猜类型；如果新增“农场布局、姜岛/火山”等类目，应先扩 typed intent 和结构化证据，再补 planner 分类规则和防串路测试。

本轮 planner 后处理边界：AI 返回具体非 `GUIDE` 类型时以 AI 分类为主；只有命中“普通任务 vs 特别订单/资源/居民”“特别订单 vs 传说鱼/鱼塘”“鱼塘产物 vs 鱼塘建筑”“农场地图 vs 农场建筑”“地下城 vs 通用攻略/资源/技能/农场地图”“通用攻略 vs 技能/制作/节日/农场地图/农场动物/普通任务/特别订单/鱼塘/怪物掉落/地下城”等高风险边界，才使用本地高置信规则纠偏；AI 明确返回 `UNKNOWN` 时只允许被拉回到普通任务 `QUEST`，避免把“鸡舍升级材料”这类问题从未知硬拉到旧命中链导致问 A 答 B。Planner 不可用、空返回或 JSON 不可解析时，才使用完整本地 fallback plan。

技能类目已从通用攻略拆出 `skillGuides` 专用 typed 数据。`SKILL` intent 现在优先返回 `skill_guide` / `skill_guide_list` evidence，通用 `GUIDE` 仍保留精通、锻造、博物馆等攻略兜底。

制作类目已从 `MACHINE` 拆出 `CRAFTING` typed intent 和 `craftingRecipes` 专用数据。制作配方现按官方制作页补到 150 条，覆盖炸弹、栅栏、洒水器、工匠设备、肥料、种子、地板/路径、钓具、戒指、可食用制作物、消耗品、照明、精炼设备、家具、储物、标牌和杂项；`MACHINE` 仍保留为兼容入口，但新分类和新测试都优先走 `CRAFTING`。

节日/活动类目已从 `GUIDE` / `SHOP` 拆出 `FESTIVAL` typed intent 和 `festivalEvents` 专用数据。全年 12 个节日/活动已完整结构化，覆盖复活节、沙漠节、花舞节、夏威夷宴会、鳟鱼大赛、月光水母起舞、星露谷展览会、万灵节、冰雪节、鱿鱼节、夜市和冬星盛宴；节日本身的问题优先走 `FESTIVAL`，具体商品购买仍可走 `SHOP`。

农场地图类目已从 `GUIDE` / `BUILDING` 拆出 `FARM_MAP` typed intent 和 `farmMaps` 专用数据。8 张开局农场地图已完整结构化，覆盖标准、河流、森林、山顶、荒野、四角、海滩和草原农场；农场选择、布局特点、适合人群、洒水器限制、蓝草/鸡舍开局、硬木、矿区、钓鱼水域等走 `FARM_MAP`，鸡舍、畜棚、筒仓、鱼塘、房屋升级等建筑材料和价格仍走 `BUILDING`。

农场动物类目沿用 `ANIMAL_CARE` intent，但不再只是通用养殖攻略。鸡舍/畜棚动物已结构化为 `farmAnimals` 专用数据，覆盖购买/获得方式、成熟时间、产物频率、采集工具、加工产物、好感/心情/运气/天气等机制和赚钱建议。单个动物或动物产物问题走 `farm_animal_detail`，例如“兔子的脚怎么出”“猪松露怎么赚钱”“奶牛为什么不出大壶牛奶”；“动物有哪些”“后期养什么动物赚钱”走 `farm_animal_available`；“动物怎么养、怎么提高心情”仍回到通用 `guide`。

地下城/冒险地点类目已从 `GUIDE` / `RESOURCE` / `SKILL` 边界中拆出 `DUNGEON` typed intent 和 `dungeonGuides` 专用数据。矿井、骷髅洞穴、火山地牢、采石场矿洞、突变虫穴、女巫沼泽等地点的解锁、层数/结构、路线、机制、奖励、任务和打法建议走 `DUNGEON`，例如“矿井多少层”“骷髅洞穴100层怎么冲”“火山地牢怎么过”“金镰刀在哪拿”；具体物品来源、怪物掉落和鱼类问题仍分别走 `RESOURCE`、`MONSTER_DROP`、`FISH`，例如“铱矿石怎么刷”“煤尘精灵掉什么”“虚空鲑鱼在哪钓”。

普通任务类目已从 `RESOURCE` / `VILLAGER_SCHEDULE` / `SPECIAL_ORDER` 边界中拆出 `QUEST` typed intent 和 `storyQuests` 专用数据。普通剧情任务、任务日志任务和多阶段任务链走 `QUEST`，例如“罗宾斧头在哪”“镇长短裤怎么拿”“神秘齐怎么做”“海盗妻子任务流程”；特别订单板和齐先生核桃房任务仍走 `SPECIAL_ORDER`。`The Mysterious Qi` 这类官方表格多行任务按任务链合并为一个条目，步骤放入结构化 walkthrough。

## 已覆盖范围

已结构化覆盖鱼类、收集包、居民、作物、建筑/工具、完整制作配方、机器兼容、商店/兑换、资源、怪物、鱼塘、料理/书籍、普通任务/任务日志、特别订单/齐先生任务、技能、节日/活动、农场地图、农场动物、地下城/冒险地点、通用攻略等高频类目。

当前数据量级：鱼类 74 条，收集包 58 个，居民 34 位，作物 30 个，建筑 27 个，工具 6 类，完整制作配方 150 个，机器兼容数据 80 个，商店/兑换 29 个，资源 181 项，怪物 58 个，鱼塘 73 条，料理/饮品 83 道，书籍 26 本，普通任务 55 个，特别订单 28 个，技能攻略 9 个，节日/活动 12 个，农场地图 8 个，农场动物 11 个，地下城/冒险地点 6 个，通用攻略 39 项。

居民已完成可送礼居民的首轮可发布覆盖：生日、礼物偏好、普通日程和高频雨天/星期/诊所/上课规则。更复杂的姜岛度假、婚后、好感剧情、绿雨、沙漠节期间特殊行为等放入后续增强。

## 验证结论

最近一次星露谷聚焦单元测试通过：`251 tests, 0 failures`。

最近一次 `bb-bot-server` reactor compile 通过：`BUILD SUCCESS`。

JSON 轻量校验通过：`dungeonGuides=6`，`craftingRecipes=150`，`storyQuests=55`，`specialOrders=28`，`festivalEvents=12`，`farmMaps=8`，`farmAnimals=11`，`machines=80`，`resources=181`，`guides=39`，`skillGuides=9`；重复 id 为空。`git diff --check` 对本轮相关文件通过。

本轮机制修正同时覆盖两个容易产生非预期回答的点：资源 fallback 增加“怎么刷”，避免“铱矿石怎么刷”落到 `UNKNOWN`；鱼类查询从首个包含匹配改为评分匹配，并让居民日程 fallback 排除钓鱼问题，避免“虚空鲑鱼在哪钓”被居民位置或普通“鲑鱼”短名抢走。

实际服务启动测试当前按目标暂停；历史本地服务测试中，非星露谷链路仍有既有 Splatoon3 A13 专用工具路由失败，不影响本功能发布判断。

## 后续原则

后续优先补齐农场布局摆放细节、居民复杂规则、姜岛岛屿地点/商店/鹦鹉解锁、宠物/马/史莱姆等偏门动物数据。每轮补数都要同步补 typed intent、防串路单元测试、JSON 校验、聚焦单元测试、reactor compile 和 `git diff --check`。

测试重心也应逐步迁移：新增类目优先覆盖 `StardewQueryPlannerServiceTest`、`StardewGuideRetrieverTest`、typed `StardewGuideService.answerEvidence` 和 `StardewGuideAssistantServiceTest`；旧 `answer(String)` 测试只保留存量兼容和少量关键回归，避免为了旧路由继续堆命中规则。
