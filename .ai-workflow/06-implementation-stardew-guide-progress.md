# 星露谷攻略助手实现进度

> 阶段：Implementation progress  
> 日期：2026-06-25  
> 分支：`ai/feature-stardew-guide`  
> worktree：`/Users/renyuming/IdeaProjects/bbBot-stardew-guide`

## 目标

构建一个可在 bbBot 中使用的星露谷攻略检索工具，支持命令和 AI tool 自然语言调用。最终目标不是只覆盖固定命令，而是尽量覆盖玩家游玩过程中临时想到的攻略问题。

明确排除：不做存档解析，不读取玩家本地存档。

2026-06-25 目标更新：先不做运行实际服务测试；后续推进以开发、单元测试、编译验证为主。

## 当前实现

### 入口

- 聊天命令：`/星露谷 ...` / `星露谷 ...` / `/stardew ...`
- AI tool：`stardew_guide`

### 检索策略

1. 优先使用本地结构化知识库：
   - 鱼类
   - 收集包
   - 作物成熟天数、季节、收益、收集包用途
   - 建筑/动物设施/房屋升级材料、花费、解锁内容
   - 工具升级/鱼竿购买解锁的费用、材料、前置和效果
   - 机器/加工设备的配方材料、输入输出、加工时间、收益规则和路线建议
   - 高频商店/商人营业时间、购买价格、兑换物和解锁条件
   - 居民日程
   - 居民生日和礼物偏好
   - 资源获取
   - 通用攻略条目，如工具升级、建筑、作物推荐、技能、进度解锁、烹饪/制作
2. 本地结构化知识库未命中时，走官方中文 Wiki API 兜底：
   - `query&list=search`
   - `parse&prop=text|displaytitle`
   - 对自然语言查询做核心词改写，例如 `战斗技能如何快速升级` -> `战斗`
   - 聚合多个候选词结果并按相关性排序，避免原始长句弱命中压过核心页面
   - 摘要提取段落、列表和部分 Wiki 表格行，增强材料/价格/配方类问题的兜底答案

### 当前数据规模

- 鱼类/钓鱼收藏物：74 条，覆盖常见钓竿鱼、夜市潜艇鱼、矿井鱼、沙漠鱼、特殊区域鱼、姜岛鱼、蟹笼产物、五条传说鱼、大家族任务传说鱼 II，以及 1.6 钓鱼果冻
- 收集包：11 个
- 作物：30 个，覆盖春/夏/秋/冬/姜岛/温室核心作物的成熟天数、种子价格/来源、基础售价、约 g/天、收集包/料理/任务用途
- 建筑：18 个，覆盖鸡舍/畜棚三段、筒仓、鱼塘、马厩、小屋/大小屋、磨坊、水井、史莱姆屋、出货箱、三段房屋升级的花费、材料、建造时间、解锁内容和推荐路线
- 工具：6 类，覆盖斧头、镐子、喷壶、锄头、垃圾桶的铜/钢/金/铱升级线，以及鱼竿购买/解锁线
- 机器/加工设备：17 个，覆盖小桶、罐头瓶、蛋黄酱机、奶酪压制机、织布机、产油机、蜂房、熔炉、木炭窑、回收机、种子生产器、宝石复制机、避雷针，以及 1.6 的鱼熏机、脱水机、诱饵制造机、蘑菇木桩
- 商店/商人：9 个高频条目，覆盖皮埃尔、铁匠、罗宾、玛妮、威利、科罗布斯、旅行货车、沙漠商人、书商，以及背包、干草、铱制洒水器、楼梯、鱼竿/鱼饵、技能书等常问商品/兑换
- 居民：34 位可送礼居民资料均已有本地结构化日程；覆盖普通日程、常驻居民、高频雨天/星期/上课/诊所规则，并保留节日、姜岛度假、婚后、好感剧情等覆盖风险提示
- 资源获取：19 项，覆盖硬木、电池组、铱矿石、五彩碎片、上古种子、布料、三种钓鱼果冻，以及煤炭、黏土、纤维、苔藓、精炼石英、三类树液采集物、干草、海草/绿藻、树液、铜铁金矿石等高频材料
- 通用攻略：33 项

这不是完整星露谷数据集。当前靠 Wiki 兜底补足未建模问题。

## 已验证问题类型

- 夏季能钓的鱼
- 夏季雨天海边能钓的鱼
- 春季海边、秋季雨天夜间、冬季矿井、夜市潜艇、姜岛等鱼类条件查询
- 蟹笼、淡水蟹笼、海水蟹笼能抓什么
- 五条传说鱼有哪些、齐先生大家族任务传说鱼有哪些
- 1.6 钓鱼果冻：三种果冻怎么钓、海果冻/洞穴果冻在哪获取
- 瀑布鱼、冬季海鱼、冬季淡水鱼等补充鱼类查询；`Dorado` 中文名修正为官方“麻哈脂鲤”
- 夏季/秋季/冬季/春季作物列表和收益排序
- 具体作物详情，如蓝莓几天成熟、防风草要不要留收集包、远古水果是否适合温室
- 进度攻略和作物查询路由分流：温室怎么修仍走温室攻略，夏季种什么走作物列表
- 某条鱼详情
- 海鱼收集包需要什么
- 居民在指定季节/日期/时间/天气下的位置
- 缺少居民定位条件时提示用户补充
- 居民生日、最爱礼物、喜欢礼物和送礼提示
- 34 位可送礼居民的生日、首批最爱/喜欢礼物和结构化日程识别；新增居民如亚历克斯、玛鲁、科罗布斯、文森特等位置问题可直接本地回答
- 居民定位只强制要求游戏内时间；如果补充季节、日期、星期、天气，会优先使用更具体规则
- 通用送礼/好感规则
- 高频商店/购买问题：背包升级多少钱、铱制洒水器在哪里买、干草在哪里买
- 高频商人日程/兑换：书商什么时候来、沙漠商人楼梯怎么换、罗宾商店几点开门
- 硬木/电池组获取
- 高频资源获取：煤炭、黏土、橡树树脂、海草/绿藻等可直接本地回答
- 资源和机器路由边界：`橡树树脂怎么获得，小桶缺这个` 走资源获取，不被小桶机器详情抢走
- 工具升级费用、材料、顺序限制
- 斧头/喷壶/镐子等具体工具升级；支持钢斧、铱镐、玻璃纤维鱼竿等具体档位
- 工具升级问法的语义保护：`金钱` 不会误判为 `金斧`，具体档位需命中完整升级名或 `金级/铱级`
- 农场建筑列表
- 鸡舍升级材料、金额、鸭/恐龙/孵化器解锁
- 筒仓材料、容量和前期路线建议
- 豪华畜棚材料、金额、猪/绵羊/自动喂食解锁
- 加工机器列表和分类查询
- 小桶配方、酿酒收益规则和远古水果/杨桃建议
- 鱼熏机配方、三种果冻材料、熏鱼售价规则
- 脱水机、诱饵制造机、蘑菇木桩等 1.6 机器材料、解锁和使用建议
- 资源和机器路由保护：`电池组怎么获得` 仍走资源获取；`三种果冻怎么钓`/`海果冻怎么获取` 仍走鱼类/钓鱼收藏物
- 夏季作物推荐
- 沙漠解锁、温室修复与种植建议、骷髅洞穴准备
- 博物馆捐赠、鱼塘、烹饪、制作、精通系统等机制类攻略
- 书商/技能书、职业重置、技能食物 buff
- 本地库没有建模的问题走 Wiki 兜底，如姜岛金核桃
- 五大技能结构化攻略：耕种、采矿、觅食、钓鱼、战斗
- 技能攻略包含经验来源、快速升级路线、5/10 级职业选择建议
- “战斗技能如何快速升级”已改为本地结构化答案，不再只靠 Wiki 兜底
- handler 回复包含答案、来源、数据版本
- Wiki API 搜索结果解析、相关性排序、正文和表格摘要提取

## 验证命令

```bash
'/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn' \
  -Dmaven.repo.local=/Users/renyuming/Documents/develop/maven/repository \
  -pl bb-bot-server \
  -Dtest=StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest \
  test
```

结果：61 tests, 0 failures。

```bash
'/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn' \
  -Dmaven.repo.local=/Users/renyuming/Documents/develop/maven/repository \
  -pl bb-bot-server -am -DskipTests compile
```

结果：BUILD SUCCESS。

2026-06-25 建筑结构化检索补充后再次执行上述两条 Maven 验证命令：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：53 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

本轮还修复了一个测试捕获到的中文短别名误命中问题：`冬季矿井能钓什么鱼` 中的“井”不能误匹配到“水井”，1 字建筑别名现在只在完全等于查询时生效。

2026-06-25 工具升级结构化检索补充后再次执行上述两条 Maven 验证命令：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：56 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

本轮还修复了一个测试捕获到的档位误命中问题：`斧头升级需要什么条件和金钱` 中的“金钱”不能误匹配为“金斧”。

2026-06-25 机器/加工设备结构化检索补充后再次执行上述两条 Maven 验证命令：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：61 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

本轮还修复了两个测试捕获到的路由回归：

- `三种果冻怎么钓` 不能被资源获取抢走，应继续走鱼类条件查询。
- `海果冻怎么获取` 已在鱼类库结构化，继续走鱼类详情，不被资源获取抢走。

2026-06-25 高频资源获取结构化补充后再次执行上述两条 Maven 验证命令：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：62 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

本轮还修复了一个测试捕获到的路由边界：`橡树树脂怎么获得，小桶缺这个` 应优先走资源获取，而不是因为提到小桶就返回机器详情。

2026-06-25 居民优先与高频商店可发布范围补充：

- 本地库新增 9 个高频商店/商人条目：皮埃尔、铁匠、罗宾、玛妮、威利、科罗布斯、旅行货车、沙漠商人、书商。
- 新增商店商品/服务检索路由，覆盖背包升级、干草、铱制洒水器、楼梯兑换、鱼竿/鱼饵、书商日期等常问问题。
- 居民位置查询改为优先本地回答。对暂未结构化精确分时日程的居民，不再直接 Wiki fallback，而是返回住址/常见可找点、查询条件、覆盖风险和顺路送礼建议。
- 后期/偏门内容，如全量居民精确日程、姜岛度假覆盖、方尖塔/黄金钟、全机器/全矿物/全怪物掉落，继续列入后续补齐范围。

本轮验证结果：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：66 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-25 居民日程补齐：

- 24 位此前只有礼物资料、没有 schedules 的居民已补本地结构化日程：亚历克斯、艾利欧特、哈维、山姆、谢恩、艾米丽、海莉、玛鲁、卡洛琳、德米特里厄斯、矮人、艾芙琳、乔治、格斯、贾斯、乔迪、肯特、科罗布斯、雷欧、刘易斯、莱纳斯、桑迪、文森特、法师。
- 当前 34/34 位可送礼居民都有 `schedules`，仓库测试已强制校验每位居民至少有一个日程规则和事件。
- 居民位置查询从“必须有季节+时间”放宽为“至少要有游戏内时间”；星期/日期/季节/天气作为可选精确条件。

本轮验证结果：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：67 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-25 料理配方与食物 buff 结构化检索补充：

- 本地库新增 19 个高频料理/饮品条目，覆盖骷髅洞穴、幸运、移速、钓鱼、采矿、战斗、耕种、觅食等常问场景。
- 新增料理详情路由，支持“幸运午餐怎么做”“香辣鳗鱼材料和效果”等问题，返回材料、配方来源、buff、推荐用法和来源。
- 新增料理推荐列表路由，支持“骷髅洞穴吃什么料理 buff 好”“钓鱼料理有哪些”“战斗等级低吃什么食物”等泛问法。
- AI tool 描述补充料理配方、食物/饮料 buff、骷髅洞穴/钓鱼/战斗/耕种推荐吃什么。

本轮验证结果：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：71 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 社区中心收集包结构化补齐：

- 本地库收集包从 11 个扩展到 37 个，覆盖 30 个普通社区中心收集包、废弃 Joja 超市“失踪的收集包”，以及工艺室、茶水间、鱼缸、锅炉房、布告栏、金库等房间级概览问法。
- 补齐此前缺失的秋/冬/异国情调采集、春/夏/秋作物、动物、蟹笼、锅炉房三小包、布告栏五小包、金库四金额包和电影院相关物品。
- 新增查询覆盖：“秋季作物收集包需要什么”“蟹笼收集包交哪几个”“布告栏染料收集包需要什么”“金库一共要多少钱”“电影院收集包要什么”。
- 仓库测试现在强制校验普通社区中心包、失踪收集包和房间概览包的 id 覆盖，以及每个收集包都有房间、奖励、物品和来源。

本轮验证结果：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：74 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 交互方向调整与稀有资源补充：

- 产品方向从“确定性路由直接给最终答案”调整为“检索证据 + AI 自然整合”。`stardew_guide` 工具现在返回 `evidence` 和回复指令，不再返回 `sourceUrls`、`gameVersion`、`lastCheckedAt` 等内部字段给 AI 上层直接展示。
- `/星露谷` 命令回复不再拼接来源、数据版本、校验日期；兜底文案也去掉“本地结构化库/官方 Wiki/来源”等内部措辞，改为自然说明“找到可能相关内容”。
- 资源获取本地库从 19 项扩到 35 项，新增恐龙蛋、恐龙蛋黄酱、矮人卷轴、兔子的脚、鱼籽酱、鹦鹉螺、红叶卷心菜、蕨菜、松露、鸭毛、海蓝宝石、虚空鲑鱼、鱿鱼墨汁、灵外质、放射性矿石、龙牙等高频稀有/后期物品。
- 路由优先级做了小幅收敛：工具/商店优先于资源，明确资源获取优先于居民和建筑，解决“矮人卷轴在哪刷”“恐龙蛋怎么获得”等被居民/建筑误抢的问题。

本轮验证结果：

- `StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：78 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

工具升级结构化后再次执行本地环境验证：

- `./scripts/dev/bb-dev.sh up --build`：build、mock OpenAI、bbBot 启动均成功，输出 `bbBot 已就绪`。
- `./scripts/dev/bb-dev.sh test`：仍为 19/20 通过；唯一失败仍是既有 Splatoon3 A13 专用工具路由场景。
- 验证后已执行 `./scripts/dev/bb-dev.sh down`，mock/bot 停止；`misu-mysql-local` 保持 `Up`。

2026-06-25 机器/加工设备结构化检索补充后，曾再次执行本地环境验证；随后目标更新为“先不做运行实际服务测试”，后续不再启动实际服务：

- `./scripts/dev/bb-dev.sh up --build`：build、mock OpenAI、bbBot 启动均成功，输出 `bbBot 已就绪`。
- `./scripts/dev/bb-dev.sh test`：仍为 19/20 通过；唯一失败仍是既有 Splatoon3 A13 专用工具路由场景。
- 目标更新后已执行 `./scripts/dev/bb-dev.sh down`，mock/bot 停止；`misu-mysql-local` 保持 `Up`。

```bash
./scripts/dev/bb-dev.sh up --build
```

结果：本地服务未能启动。失败原因是 Docker daemon 无法连接，`misu-mysql-local` 容器未运行；`bbtest` profile 依赖 `localhost:3316/bb_bot_local`。这属于本机依赖未就绪，不是本次代码编译或单测失败。

2026-06-25 补充验证：新增蟹笼/传说鱼覆盖后再次执行 `./scripts/dev/bb-dev.sh up --build`，仍然失败于同一环境问题：

```text
Cannot connect to the Docker daemon at unix:///Users/renyuming/.docker/run/docker.sock. Is the docker daemon running?
[bb-dev] MySQL 容器 misu-mysql-local 没在跑。请到 misu-server 那边 ./dev.sh up 把它起来。
```

2026-06-25 再次补充验证：用户启动 Docker 后再次执行 `./scripts/dev/bb-dev.sh up --build`，Docker daemon 已可用，但仍被本地 MySQL 依赖阻塞：

```text
[bb-dev] MySQL 容器 misu-mysql-local 没在跑。请到 misu-server 那边 ./dev.sh up 把它起来。
```

2026-06-25 作物结构化检索补充后再次执行 `./scripts/dev/bb-dev.sh up --build`，仍然失败于同一 MySQL 依赖：

```text
[bb-dev] MySQL 容器 misu-mysql-local 没在跑。请到 misu-server 那边 ./dev.sh up 把它起来。
```

2026-06-25 环境修复：直接执行 `docker start misu-mysql-local`，容器恢复运行，端口 `3316->3306` 可用。随后用长驻 shell 保持 `bb-dev.sh up --build` 启动的 mock OpenAI 和 bbBot 进程存活，本地服务启动成功：

```text
bbBot 已就绪
mock running
bot running
mysql(misu-mysql-local) Up
```

随后执行 `./scripts/dev/bb-dev.sh test`，结果：19/20 通过。唯一失败项为既有 Splatoon3 A13 专用工具路由场景，mock LLM 返回普通流式聊天文本，没有触发 Splatoon 专用工具；其余 BB 协议、工具调用、文件、搜索、skill、streaming、steering、并行工具场景均通过。该失败不属于星露谷攻略功能路径，但说明全仓端到端回归尚未全绿。

2026-06-25 建筑结构化检索补充后再次启动本地环境：

- `docker ps --filter name=misu-mysql-local`：`misu-mysql-local Up`，端口 `3316->3306`。
- `./scripts/dev/bb-dev.sh up --build`：数据库初始化、build、mock OpenAI、bbBot 启动均成功，输出 `bbBot 已就绪`。
- `./scripts/dev/bb-dev.sh test`：仍为 19/20 通过；唯一失败仍是既有 Splatoon3 A13 专用工具路由场景。
- 验证后已执行 `./scripts/dev/bb-dev.sh down`，mock/bot 停止；MySQL 容器保持运行。

2026-06-26 `/星露谷` 命令接入 AI 检索整合链路：

- 新增 `StardewGuideAssistantService`，命令入口现在和 AI tool 一样走“关键词扩展 -> 多次攻略检索 -> 证据整合 -> 自然语言回复”的路径。
- 关键词扩展通过 `AiChatService` 的 LIGHT 档完成，要求保留季节、地点、天气、时间、居民名、物品名、建筑名，以及“怎么获得/怎么做/升级材料/在哪里”等动作信息。
- 证据整合通过 `AiChatService` 的 CHAT 档完成，系统提示要求只根据检索资料回答，回复自然、简洁、可执行，并且不提证据、关键词、数据版本、校验日期、来源链接、Wiki 或本地库。
- `/星露谷` handler 不再直接调用 `StardewGuideService`，改为调用 `StardewGuideAssistantService`；命令回复不再拼接来源、数据版本、校验日期。
- AI 关键词输出支持 JSON 字符串数组和宽松编号列表；AI 返回空或抛异常时，回退到单次本地攻略检索，避免命令入口直接失败。
- 新增详细 QA 测试方案：`.ai-workflow/09-qa-stardew-guide-ai-retrieval-test-plan.md`。

本轮验证结果：

- `StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：82 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

## 真实网络验证

已用 `curl` 验证官方中文 Wiki API：

- 长自然语言 `战斗技能如何快速升级` 直接搜索无结果。
- 核心词 `战斗` 可搜索到页面。
- `parse` API 可返回 `战斗` 页面 HTML。

因此实现中加入了 query rewriting，不能只把用户原句丢给 Wiki。

## 尚未完成

完整目标尚未达成，原因：

- 本地结构化数据已覆盖一版可发布的高频问题，但还不是完整星露谷百科。
- Wiki 兜底摘要可以回答很多未建模问题，但不等同于完整结构化攻略。
- 本地服务历史上已可启动，`./scripts/dev/bb-dev.sh test` 当前 19/20 通过；唯一失败为既有 Splatoon3 A13 专用工具路由场景。按最新目标，先不再运行实际服务测试。
- 尚未覆盖全量数据质量测试，例如全部鱼完整收集、全部作物、全部建筑、全部技能、全部烹饪/制作/怪物/矿物条目。
- 居民已做到 34 位送礼资料和首批结构化日程全覆盖；节日、姜岛度假、婚后、好感剧情、绿雨、沙漠节等复杂覆盖规则还未逐条精确建模。
- 商店已优先覆盖 9 个高频购买/兑换入口；赌场、绿洲、探险家公会全量武器/戒指、姜岛/火山/齐钻商店等偏后期商店还未完整结构化。
- 建筑已新增首批 18 个核心条目，但方尖塔、黄金钟、社区升级、多人小屋不同样式、潘姆房屋等后期/特殊建筑还未结构化。
- 工具升级已结构化首批 6 类工具，但附魔、锻造、鱼竿浮标/鱼饵搭配和特殊工具还未完整结构化。
- 机器/加工设备已新增首批 17 个核心条目，但重型熔炉、重型树液采集器、骨头磨坊、虫饵盒、豪华虫饵盒、史莱姆蛋压制机/孵化器、太阳能板、木材削片机、蟹笼、洒水器、肥料、炸弹、箱子、标牌和后期特殊设备还未完整结构化。
- 资源获取已扩到 19 项高频材料，但全矿物、全怪物掉落、全姜岛/火山材料、全鱼塘产物和特殊货币还未完整结构化。
- 技能攻略目前覆盖五大基础技能、精通概览、书商/技能书、职业重置、技能食物 buff；后续还需继续补具体书籍效果、具体料理 buff 数值、技能相关装备/附魔等细节。

## 后续补齐方向

1. 数据覆盖：
   - 全鱼类剩余补齐：继续核对全鱼表、魔法鱼饵提示和全鱼收集策略；虾虎鱼、三种果冻已补
   - 全收集包，包括重混收集包
   - 居民复杂覆盖规则：节日、姜岛度假、婚后、好感剧情、绿雨、沙漠节等；生日、首批礼物偏好和普通/高频日程已覆盖 34 位可送礼居民
   - 全作物、果树、季节、成熟天数、基础收益、收集包用途；首批 30 个核心作物已结构化
   - 全建筑、房屋升级、动物、机器；首批核心农场建筑、房屋升级和 17 个核心机器已结构化，后续补方尖塔/黄金钟/社区升级/特殊建筑和全部机器设备
   - 全工具、鱼竿、附魔、锻造、特殊工具和使用路线；首批工具升级费用/材料已结构化
   - 全技能升级、职业选择、经验获取、快速升级路线、Mastery、技能书、职业重置、技能食物 buff
   - 矿井、骷髅洞穴、火山、怪物掉落、博物馆、烹饪、制作；首批 19 个高频资源获取条目已结构化
2. 测试覆盖：
   - 每个 intent 至少 happy path + miss + alias + 条件过滤
   - Wiki 兜底 query rewriting 回归样例
   - handler/tool 输出契约
   - 数据完整性校验测试
3. 验证策略：
   - 当前阶段先不运行实际服务测试。
   - 每轮继续执行星露谷相关单元测试。
   - 每轮继续执行 `bb-bot-server` reactor compile。
