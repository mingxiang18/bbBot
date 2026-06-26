# 星露谷攻略助手实现进度

> 阶段：Implementation progress  
> 日期：2026-06-26
> 分支：`master`
> worktree：`/Users/renyuming/IdeaProjects/bbBot`

## 目标

构建一个可在 bbBot 中使用的星露谷攻略检索工具，支持命令和 AI tool 自然语言调用。最终目标不是只覆盖固定命令，而是尽量覆盖玩家游玩过程中临时想到的攻略问题。

明确排除：不做存档解析，不读取玩家本地存档。

2026-06-25 目标更新：先不做运行实际服务测试；后续推进以开发、单元测试、编译验证为主。

## 当前实现

### 入口

- 聊天命令：`/星露谷 ...` / `星露谷 ...` / `/stardew ...`
- AI tool：`stardew_guide`

### 检索策略

1. `/星露谷` 命令和 `stardew_guide` AI tool 统一走 `StardewGuideAssistantService`：
   - 先由 `StardewQueryPlannerService` 调用 `AiChatService` LIGHT 档，把用户问题规划为 1-4 个 intent。
   - 每个 intent 都包含类型、类型内检索关键词和季节/地点/天气/时间/居民等约束。
   - 再由 `StardewGuideRetriever` 按类型添加检索 hint，分别检索证据，最后由 `AiChatService` CHAT 档整合成自然回复。
2. 优先使用本地结构化知识库：
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
   - 通用攻略条目，如工具升级、建筑、作物推荐、技能、进度解锁、烹饪/制作、精通系统、火山锻造/附魔
3. 本地结构化知识库未命中时，走官方中文 Wiki API 兜底：
   - `query&list=search`
   - `parse&prop=text|displaytitle`
   - 对自然语言查询做核心词改写，例如 `战斗技能如何快速升级` -> `战斗`
   - 聚合多个候选词结果并按相关性排序，避免原始长句弱命中压过核心页面
   - 摘要提取段落、列表和部分 Wiki 表格行，增强材料/价格/配方类问题的兜底答案

### 当前数据规模

- 鱼类/钓鱼收藏物：74 条，覆盖常见钓竿鱼、夜市潜艇鱼、矿井鱼、沙漠鱼、特殊区域鱼、姜岛鱼、蟹笼产物、五条传说鱼、大家族任务传说鱼 II，以及 1.6 钓鱼果冻
- 收集包：58 个，覆盖普通社区中心收集包、房间级概览、金库金额包、废弃 Joja 超市“失踪的收集包”，以及 21 个重混/随机收集包条目
- 作物：30 个，覆盖春/夏/秋/冬/姜岛/温室核心作物的成熟天数、种子价格/来源、基础售价、约 g/天、收集包/料理/任务用途
- 建筑：27 个，覆盖鸡舍/畜棚三段、筒仓、鱼塘、马厩、小屋/大小屋、磨坊、水井、史莱姆屋、出货箱、三段房屋升级、法师塔魔法建筑、姜岛农场方尖塔、黄金钟、祝尼魔小屋、潘姆房屋和城镇捷径社区升级的花费、材料、建造时间、解锁内容和推荐路线
- 工具：6 类，覆盖斧头、镐子、喷壶、锄头、垃圾桶的铜/钢/金/铱升级线，以及鱼竿购买/解锁线
- 机器/加工/制作设备/常用 craftable：80 个，覆盖小桶、罐头瓶、蛋黄酱机、奶酪压制机、织布机、产油机、蜂房、熔炉、木炭窑、回收机、种子生产器、宝石复制机、避雷针，以及 1.6 的鱼熏机、脱水机、诱饵制造机、蘑菇木桩；新增洒水器、炸弹、楼梯、箱子、标牌、稻草人、太阳能板、重型熔炉、史莱姆设备、骨头磨坊、晶球破开器、料斗、农场电脑、肥料/土壤、生长激素、图腾、怪物香水、仙尘、戒指、鱼饵、钓具、蟹笼等常问制作内容
- 商店/商人：29 个常用/特殊/节日条目，覆盖皮埃尔、铁匠、罗宾、玛妮、威利、科罗布斯、旅行货车、沙漠商人、书商、冒险家公会、矮人商店、绿洲、姜岛商人、齐先生核桃房、星之果实餐吧、哈维的诊所、JojaMart、赌场、火山矮人商店、法师塔、冰淇淋摊、废弃屋帽子店、浣熊商店、复活节商店、花舞节商店、月光水母节商店、星露谷展览会商店、万灵节商店、沙漠节商店，以及背包、干草、铱制洒水器、楼梯、鱼竿/鱼饵、技能书、武器、炸弹、杨桃种子、姜岛树苗、齐钻商品、体力药、自动抚摸机、赌场兑换、岛屿传送图腾配方、魔法建筑、冰淇淋、帽子、浣熊兑换、草莓种子、花桶配方、节日稀有稻草人、海泡布丁、星之果实、沙漠节卡利科三花蛋兑换等常问商品/兑换
- 居民：34 位可送礼居民资料均已有本地结构化日程；覆盖普通日程、常驻居民、高频雨天/星期/上课/诊所规则，并保留节日、姜岛度假、婚后、好感剧情等覆盖风险提示
- 资源获取：91 项，覆盖硬木、电池组、铱矿石、五彩碎片、上古种子、布料、三种钓鱼果冻，煤炭、黏土、纤维、苔藓、精炼石英、三类树液采集物、干草、海草/绿藻、树液、铜铁金矿石等高频材料，以及太阳精华、虚空精华、蝙蝠翅膀、史莱姆泥、虫肉、骨头碎片等高频怪物掉落，恐龙蛋、恐龙蛋黄酱、矮人卷轴、兔子的脚、鱼籽酱、龙牙、远古斑点、古物宝藏、四类晶球、基础矿物/宝石、一批高频博物馆古物、动物产品和果树水果
- 料理/饮品：83 道，覆盖官方 81 种可烹饪菜品，并额外保留咖啡、魔法糖冰棍等玩家常问但不属于厨房烹饪的实用食物/饮品条目；支持骷髅洞穴/钓鱼/采矿/战斗/耕种/觅食/姜岛料理、普通回复、配方材料、buff 效果等查询
- 书籍/力量书/技能书：26 本，覆盖 1.6 全部常见书籍条目，包括价格目录、风之道、马之书、老滑腿、怪物图鉴、洞穴系统地图、矮人安全手册、捕蟹的艺术、海之宝石、伍迪的秘密、浣熊日志、星之书、五类技能书、酱料女皇食谱等；支持具体书名效果、重复阅读、获取方式、是否值得优先读
- 通用攻略：36 项，新增火山锻造与附魔，并加厚精通系统；覆盖武器宝石锻造、工具/武器附魔、无限武器、银河之魂、戒指合成、精通点、五系精通奖励和精通优先级

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
- 重混/随机收集包：重混春季作物收集包、重混酿酒师收集包、重混优质鱼收集包、重混冒险家收集包、重混帮手收集包、重混冬日星盛宴收集包等；明确重混同名包优先，不被普通冒险家/作物/鱼类包抢走
- 居民在指定季节/日期/时间/天气下的位置
- 缺少居民定位条件时提示用户补充
- 居民生日、最爱礼物、喜欢礼物和送礼提示
- 34 位可送礼居民的生日、首批最爱/喜欢礼物和结构化日程识别；新增居民如亚历克斯、玛鲁、科罗布斯、文森特等位置问题可直接本地回答
- 居民定位只强制要求游戏内时间；如果补充季节、日期、星期、天气，会优先使用更具体规则
- 通用送礼/好感规则
- 高频商店/购买问题：背包升级多少钱、铱制洒水器在哪里买、干草在哪里买
- 高频商人日程/兑换：书商什么时候来、沙漠商人楼梯怎么换、罗宾商店几点开门
- 常用商人/兑换商店：冒险家公会几点开、熔岩武士刀在哪里买、炸弹在哪里买、杨桃种子在哪里买、香蕉树苗怎么换、马笛在哪里买、咖啡在哪里买
- 特殊商店/后期商人：体力药在哪里买、自动抚摸机在哪里买、赌场怎么进、岛屿图腾配方在哪里买、幻觉神龛多少钱、冰淇淋在哪里买、帽子在哪里买、胡萝卜种子在哪里买
- 节日商店/活动兑换：草莓种子在哪里买、沙漠节换什么、星之果实展览会在哪里买、海泡布丁在哪里买、万灵节稀有稻草人2多少钱
- 硬木/电池组获取
- 高频资源获取：煤炭、黏土、橡树树脂、海草/绿藻等可直接本地回答
- 高频怪物掉落：煤尘精灵刷煤炭、太阳精华、虚空精华、蝙蝠翅膀、史莱姆泥、虫肉、骨头碎片哪里刷/怎么获得
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
- 肥料/土壤/生长激素制作：基础肥料、高级肥料、豪华肥料、生长激素、高级生长激素、超级生长激素、初级/高级/豪华保湿土壤、树肥
- 图腾/一次性消耗品制作：海滩/山岭/农场/沙漠/姜岛传送图腾、雨水图腾、宝藏图腾、怪物香水、仙尘
- 戒指制作：坚固戒指、战士戒指、尤巴戒指、荆棘戒指、光辉戒指、铱环、结婚戒指；新增测试覆盖 `铱环怎么做` 不跑到通用攻略
- 钓鱼装备制作：鱼饵、高级鱼饵、野性鱼饵、魔法鱼饵、挑战鱼饵、旋式鱼饵、精装旋式鱼饵、陷阱/声呐/软木塞/优质浮标、寻宝器、倒刺钩、磁铁、蟹笼
- 蟹笼路由边界：`蟹笼怎么做` 走制作详情，`蟹笼能抓什么` 继续走鱼类/蟹笼产物列表
- 夏季作物推荐
- 沙漠解锁、温室修复与种植建议、骷髅洞穴准备
- 博物馆捐赠、鱼塘、烹饪、制作、精通系统等机制类攻略
- 精通系统：精通点怎么刷、精通先选哪个、五系精通奖励、铱金镰刀、高级铱金鱼竿、挑战鱼饵、宝藏图腾、铁砧和迷你锻造台
- 书商/技能书、职业重置、技能食物 buff
- 火山锻造/附魔：银河剑怎么锻造、工具附魔哪个好、无限武器怎么做、银河之魂怎么用、戒指合成怎么做
- 本地库没有建模的问题走 Wiki 兜底，如姜岛金核桃
- 五大技能结构化攻略：耕种、采矿、觅食、钓鱼、战斗
- 技能攻略包含经验来源、快速升级路线、5/10 级职业选择建议
- “战斗技能如何快速升级”已改为本地结构化答案，不再只靠 Wiki 兜底
- 具体书籍/力量书/技能书：价格目录有什么用、星之书有什么用、怪物图鉴在哪里买、战斗季刊有什么用、矮人安全手册在哪里买、酱料女皇食谱怎么解锁
- 书籍检索边界：多本书同问返回书籍对照，单本书返回效果/获取/重复阅读；SHOP intent 问书籍来源时也能走书籍结构化证据，不强依赖旧商店库存命中
- handler 和 AI tool 回复自然答案，不展示数据版本、校验日期、来源链接、Wiki 或本地库等内部信息
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
- 第二批常问料理详情已覆盖“粉红蛋糕怎么做”“红之盛宴效果”“秋日恩赐材料和效果”“南瓜派怎么做”“蔓越莓酱效果”等问法；新增 typed evidence 测试，确保 COOKING intent 不跑到鱼类、建筑或工具。
- 剩余常规烹饪已补齐，新增“海鲜杂烩汤材料和效果”“香蕉布丁怎么做”“墨汁意大利饺效果”“姜岛料理有哪些”等 typed evidence 和列表问法覆盖。
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

2026-06-26 重混收集包结构化补齐：

- 本地库收集包从 37 个扩展到 58 个，新增 21 个重混/随机收集包条目。
- 覆盖重混工艺室、茶水间、鱼缸、锅炉房、布告栏的高频问法：黏黏、森林、野药、春/夏/秋重混作物、稀有作物、鱼农、花园、酿酒师、优质鱼、捕鱼大师、冒险家、宝藏猎人、工程师、儿童、采集者、家庭厨师、帮手、万灵节、冬日星盛宴。
- 每个重混包都保留奖励、物品数量、品质要求和“随机 N 选 M/重混可能抽到”等提示，方便 AI 自然回复时解释为什么玩家档案里的包可能不同。
- `StardewKnowledgeRepository.findBundle` 从顺序命中改成打分命中；用户明确说“重混/随机/remixed”时，优先匹配 `remixed_` 条目，避免“重混冒险家收集包”被普通冒险家收集包抢走。
- `stardew_guide` tool 描述补充“重混随机收集包”，提高工具入口召回率。
- 测试补充：
  - 仓库测试强制校验 58 个收集包规模和 21 个 `remixed_` id。
  - `StardewGuideServiceTest` 覆盖重混春季作物、酿酒师、优质鱼、冒险家、帮手收集包问法。
  - `StardewGuideRetrieverTest` 覆盖多关键词 BUNDLE plan 下重混春季作物、鱼农、冒险家、冬日星盛宴证据，不串到资源、鱼类、作物或工具路线。

本轮聚焦验证结果：

- JSON 轻量校验：`bundles=58`，`remixed=21`，重复 id 为空，关键新增 id 无缺失。
- `git diff --check`：无 whitespace 问题。
- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest,StardewGuideToolTest`：126 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：158 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 交互方向调整与稀有资源补充：

- 产品方向从“确定性路由直接给最终答案”调整为“检索证据 + AI 自然整合”。`stardew_guide` 工具现在返回 `evidence` 和回复指令，不再返回 `sourceUrls`、`gameVersion`、`lastCheckedAt` 等内部字段给 AI 上层直接展示。
- `/星露谷` 命令回复不再拼接来源、数据版本、校验日期；兜底文案也去掉“本地结构化库/官方 Wiki/来源”等内部措辞，改为自然说明“找到可能相关内容”。
- 资源获取本地库从 19 项扩到 35 项，新增恐龙蛋、恐龙蛋黄酱、矮人卷轴、兔子的脚、鱼籽酱、鹦鹉螺、红叶卷心菜、蕨菜、松露、鸭毛、海蓝宝石、虚空鲑鱼、鱿鱼墨汁、灵外质、放射性矿石、龙牙等高频稀有/后期物品。
- 路由优先级做了小幅收敛：工具/商店优先于资源，明确资源获取优先于居民和建筑，解决“矮人卷轴在哪刷”“恐龙蛋怎么获得”等被居民/建筑误抢的问题。

2026-06-26 AI 分类检索边界收缩：

- `StardewGuideRetriever` 不再在 typed plan 无证据时无条件回退到自由文本路由；后续又进一步收紧为 `UNKNOWN` intent 也不再调用 `StardewGuideService.answer(query)` 旧自由判断链路。
- `StardewGuideAssistantService` 在已取得 typed evidence 但 CHAT 档整合失败时，优先返回已检索证据答案，不再重新走自由文本路由，降低“用户问 A、旧规则答 B”的风险。
- 对 typed plan miss 增加回归测试：例如 AI 把“鸡舍升级材料”分类成 `RESOURCE` 时，只返回资源未命中提示，不会再被自由文本规则改答成鸡舍建筑升级。
- 旧结论曾保留 `UNKNOWN` 单次兜底；当前实现已改为 AI 不可用时由 planner 生成 typed fallback plan，仍然保持“分类 -> 关键词 -> typed 检索”路径。

本轮验证结果：

- `StardewGuideRetrieverTest,StardewGuideAssistantServiceTest`：15 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：100 tests, 0 failures。

2026-06-26 证据层接口收敛：

- `StardewGuideService` 保留为本地知识库证据层，新增 `answerEvidence(StardewGuideIntent, query)` 作为显式 typed evidence API。
- `/星露谷` 命令和 `stardew_guide` AI tool 的正常链路仍统一为 `StardewQueryPlannerService -> StardewGuideRetriever -> StardewGuideAssistantService`。
- `StardewGuideRetriever` 改为只调用 `answerEvidence`，不再语义依赖旧的 `answer(String)` 自由路由入口。
- `StardewGuideAssistantService` 空问题改为调用 `helpAnswer()`，不再通过旧自由路由获取帮助文案。
- `StardewGuideService.answer(String)` 和 `answer(StardewGuideIntent, String)` 标记为兼容入口；typed `UNKNOWN` 改为返回空证据，不再回退到自由路由。
- 测试补充：新增 retriever 调用 `answerEvidence` 的交互测试，新增 `UNKNOWN` evidence 不回退旧自由路由的单元测试。

本轮聚焦验证结果：

- `StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest`：96 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：136 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 第二批常见料理结构化补齐：

- 本地料理/饮品数据从 46 道扩展到 70 道。
- 新增 24 道常问料理：粉红蛋糕、大黄派、曲奇饼、意大利面、炸鳗鱼、红之盛宴、大米布丁、冰淇淋、蓝莓千层酥、秋日恩赐、超级大餐、蔓越莓酱、填料、海藻汤、清汤、李子布丁、洋蓟蘸酱、炒菜、烤榛子、南瓜派、萝卜沙拉、水果沙拉、黑莓脆皮饼、蔓越莓糖果。
- `StardewQueryPlannerService` 和 `StardewGuideService` 的本地 fallback 菜名识别补齐第二批菜名词根，保证 AI 分类不可用时，“南瓜派怎么做”“蔓越莓酱效果”等自然问法仍归入 `COOKING`，不会落到 `UNKNOWN` 或其他类型。
- 新增/扩展测试覆盖：
  - 仓库数据规模和第二批料理 id 覆盖，强制校验料理数据不少于 70 条。
  - typed `answerEvidence(COOKING, ...)` 可返回粉红蛋糕、红之盛宴、秋日恩赐详情。
  - `StardewGuideRetriever` 对多关键词 COOKING plan 只返回料理证据，不串到鱼类、建筑、工具。
  - planner 本地 fallback 在 AI 抛错时仍能把第二批菜名材料/效果问题分类为 `COOKING`。

本轮验证结果：

- JSON 轻量校验：`cookingRecipes=70`，重复 id 为空，关键新增 id 无缺失。
- `git diff --check`：无 whitespace 问题。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest,StardewKnowledgeRepositoryTest`：130 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：141 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 常规烹饪全量补齐：

- 本地料理/饮品数据从 70 条扩展到 83 条；覆盖官方 81 种可烹饪菜品，并额外保留咖啡、魔法糖冰棍等常问实用食物/饮品条目。
- 新增 13 个剩余烹饪条目：意式烤面包、卷心菜沙拉、意式蕨菜炖饭、虞美人籽松糕、海鲜杂烩汤、法式田螺、虾鸡尾酒、香蕉布丁、芒果糯米饭、夏威夷芋泥、热带咖喱、墨汁意大利饺、苔藓汤。
- `StardewGuideService` 的料理列表筛选新增 `island` 标签识别，支持“姜岛料理有哪些”这类泛问法。
- `StardewQueryPlannerService` 和 `StardewGuideService` 本地 fallback 菜名识别补齐“墨汁意大利饺、芒果糯米饭、苔藓汤、虾鸡尾酒、海鲜杂烩汤”等词根，AI 分类不可用时仍能归入 `COOKING`。
- 新增/扩展测试覆盖：
  - 仓库数据规模不少于 83 条，并强制校验剩余 13 个料理 id。
  - typed `answerEvidence(COOKING, ...)` 可返回海鲜杂烩汤、香蕉布丁、墨汁意大利饺详情。
  - `StardewGuideRetriever` 多关键词 COOKING plan 可返回后期/姜岛/特殊效果料理证据，不串到资源获取、鱼类、建筑或工具。
  - “姜岛料理有哪些”列表问法可召回香蕉布丁、芒果糯米饭、夏威夷芋泥、热带咖喱。

本轮验证结果：

- JSON 轻量校验：`cookingRecipes=83`，重复 id 为空，关键新增 id 无缺失。
- `git diff --check`：无 whitespace 问题。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest,StardewKnowledgeRepositoryTest`：130 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：141 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

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

2026-06-26 博物馆、古物、矿物、晶球检索补充：

- 资源获取本地库从 35 项扩到 62 项，新增远古斑点、古物宝藏、晶球、冰封晶球、岩浆晶球、万象晶球、石英、地晶、冰泪晶、火水晶、紫水晶、翡翠、红宝石、钻石、矮人小工具、稀有圆盘、古代鼓、骨笛、鸡雕像、黄金面具、黄金遗物、干海星、锚、玻璃碎片、史前头骨、手部骨骼、史前肋骨等条目。
- `博物馆捐赠` 攻略条目补充 95 件目标、42 件古物、53 件矿物、生锈的钥匙、星之果实、矿物补缺、古物补缺和特殊奖励路线。
- AI tool 描述补充博物馆捐赠、古物/文物、矿物、晶球、万象晶球、古物宝藏、缺失藏品怎么补等触发场景。
- `StardewKnowledgeRepository.findResource` 从顺序命中改为按匹配分数和用户问题中的出现位置排序，避免“万象晶球怎么刷，开还是换古物宝藏”被后文“古物宝藏”抢走。
- 新增测试覆盖资源规模、博物馆古物矿物核心 id、博物馆泛问、晶球/古物宝藏/矮人小工具/钻石/黄金遗物具体问法，以及 AI tool evidence 输出。

本轮验证结果：

- `StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：85 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 AI 分类规划与 typed retrieval 重构：

- 用户反馈旧实现会陷入“命中测试地狱”，容易把新问题误判到错误路由。本轮把助手链路从“AI 生成关键词数组 -> 逐个撞 `StardewGuideService`”改为“AI 分类规划 -> 类型内关键词检索 -> typed evidence -> AI 自然整合”。
- 新增 `StardewGuideIntent`、`StardewQueryPlan`、`StardewQueryPlannerService`、`StardewGuideRetriever`、`StardewGuideEvidence`。
- 规划器要求 AI 输出 JSON：`needMoreInfo`、`clarificationQuestion`、`intents[].type`、`intents[].keywords`、`intents[].constraints`。类型覆盖鱼类、收集包、居民位置、居民资料、资源、动物、果树、作物、工具、建筑、机器、商店、料理、技能、博物馆、通用攻略和未知。
- 居民“现在在哪”类问题如果缺少游戏内时间，规划层直接返回澄清问题，不再让后续检索或自然语言整合猜答案。
- `StardewGuideRetriever` 根据 intent 添加轻量类型 hint，例如 `FRUIT_TREE` 补“果树”、`BUNDLE` 补“收集包”、`RESOURCE` 补“怎么获得”，减少“苹果树”被苹果资源抢走、“收集包 + 资源”混问互相抢路由的问题。
- `stardew_guide` AI tool 已改为调用 `StardewGuideAssistantService`，和 `/星露谷` 命令共享同一条“规划 -> 检索 -> 整合”路径。
- 本轮同时补充动物养殖、动物产品、果树和水果结构化数据；资源获取扩到 85 项，通用攻略扩到 35 项。

本轮验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：80 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 制作设备扩容与 typed 本地证据入口：

- 本地机器/加工/制作设备从 17 项扩到 39 项，新增洒水器、优质洒水器、铱制洒水器、樱桃炸弹、炸弹、超级炸弹、楼梯、箱子、大箱子、石箱、大石箱、木牌、稻草人、豪华稻草人、重型熔炉、太阳能板、史莱姆孵化器、史莱姆蛋压制机、骨头磨坊、晶球破开器、料斗、农场电脑。
- `StardewGuideService` 新增 `answer(StardewGuideIntent type, String rawQuery)` typed 本地证据入口。旧的 `answer(String)` 保留为兼容和兜底，但 AI 规划后的主检索路径不再直接走 raw query 大路由。
- `StardewGuideRetriever` 改为按 AI intent 调用 typed 本地查询，解决 `MACHINE` 问题被 `RESOURCE`/`BUILDING` 抢路由的根因；新增测试覆盖“晶球破开器/太阳能板”这种名字重叠场景。
- 路由边界补充：明确资源名出现时，`恐龙蛋黄酱怎么做` 仍走资源获取；明确机器名出现时，`晶球破开器怎么做` 走机器详情。
- AI tool 描述补充洒水器、炸弹、楼梯、箱子、标牌、稻草人、太阳能板、重型熔炉、史莱姆设备、骨头磨坊、晶球破开器等制作设备触发场景。

本轮验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：87 tests, 0 failures。

2026-06-26 高频 craftable 补齐：

- 按官方 Crafting 数据补充肥料/土壤/生长激素、传送图腾/雨水图腾/宝藏图腾、怪物香水、仙尘、戒指等高频制作条目，本地机器/加工/制作设备/常用 craftable 从 39 项扩到 65 项。
- `StardewGuideService` 的机器类别识别扩展为 `fertilizer`、`totem`、`ring`、`consumable`，typed `MACHINE` 路径可以稳定回答 `高级生长激素怎么做`、`雨水图腾怎么做`、`铱环怎么做`。
- `StardewGuideRetrieverTest` 新增 typed 证据测试，确认 `雨水图腾`、`铱环` 这类 craftable 不会回落到泛攻略或资源路线。
- AI tool 描述补充肥料、图腾、怪物香水、仙尘、戒指等触发场景。
- 新增 `mockito-extensions/org.mockito.plugins.MockMaker` 测试资源，将 Mockito 固定为 subclass mock maker，避免当前 JDK 下 inline mock maker 需要 runtime attach 导致带 mock 的单测失败。

本轮聚焦验证结果：

- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest`：80 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：92 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 钓鱼装备 craftable 补齐：

- 按官方 Crafting 的 Fishing 段补充钓具、鱼饵和蟹笼制作数据，本地机器/加工/制作设备/常用 craftable 从 65 项扩到 80 项。
- 新增 `fishing` 类别，覆盖旋式鱼饵、陷阱浮标、声呐浮标、软木塞浮标、优质浮标、寻宝器、精装旋式鱼饵、倒刺钩、磁铁、鱼饵、高级鱼饵、野性鱼饵、魔法鱼饵、挑战鱼饵、蟹笼。
- `StardewGuideService` 新增蟹笼产物分流保护：`蟹笼能抓什么` 不被新增的蟹笼制作条目抢走，仍返回淡水/海水蟹笼可抓内容。
- AI tool 描述补充鱼饵、钓具、浮标、寻宝器、旋式鱼饵、蟹笼等触发场景。

本轮聚焦验证结果：

- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest`：85 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：97 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 后期建筑/社区升级补齐：

- 按官方 Wiki 补充法师塔魔法建筑和后期传送建筑，本地建筑从 18 项扩到 27 项。
- 新增 9 个建筑条目：地球方尖塔、水之方尖塔、沙漠方尖塔、姜岛方尖塔、姜岛农场方尖塔、祝尼魔小屋、黄金钟、潘姆房屋社区升级、城镇捷径社区升级。
- `StardewGuideService` 新增 `magic` 和 `community` 建筑分类，支持“魔法建筑有哪些”“方尖塔有哪些”“社区升级有哪些”等列表问法。
- `StardewGuideRetrieverTest` 新增 typed `BUILDING` 证据测试，覆盖“沙漠方尖塔需要什么”和“潘姆房子社区升级需要什么”组合问题，确保 AI 分类后能按建筑证据检索。
- 农场方尖塔使用 `金核桃 x20` 作为材料成本，金币花费展示为“无固定金币花费”，避免把姜岛鹦鹉建筑误写成金币建筑。

本轮聚焦验证结果：

- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest`：92 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：105 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 怪物掉落/刷材料资源补齐：

- 按官方 Wiki Monster Loot、单物品页和怪物页补齐首批高频怪物掉落资源，本地资源获取从 85 项扩到 91 项。
- 强化煤炭条目：补充“煤尘精灵刷煤”“刷煤炭”“煤炭哪里刷”等别名，明确矿井 41-79 层煤尘精灵 50% 掉煤、矿井入口重置、电梯刷层、怪物香水/窃贼戒指等建议。
- 新增 6 个高频资源条目：太阳精华、虚空精华、蝙蝠翅膀、史莱姆泥、虫肉、骨头碎片。
- 每个资源条目都补齐来源、推荐刷法、常见用途和来源链接，覆盖科罗布斯购买、鱼塘产物、矿井层数、姜岛骨头矿点、特别订单/制作用途等可执行信息。
- AI tool 描述补充怪物掉落/怪物战利品触发词，确保“哪里刷材料”类问题更容易调用星露谷工具。
- 新增资料库完整性测试，要求这组高频怪物掉落资源 ID 全部存在。
- 新增 `StardewGuideServiceTest` 覆盖 7 个真实资源问法，验证输出包含刷点、价格、层数和用途。
- 新增 `StardewGuideRetrieverTest` typed `RESOURCE` 证据测试，确认 AI 已分类后能按资源检索太阳精华、蝙蝠翅膀、骨头碎片，不依赖旧自由文本兜底。

本轮聚焦验证结果：

- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest`：95 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：108 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 战斗技能快速升级攻略加厚：

- 针对用户目标里的“战斗等级低想知道战斗技能如何快速升级”补强本地结构化 `combat_skill` 攻略，不再只是概览。
- 按官方 Combat/Skills/Weapons/Adventurer's Guild/Combat Quarterly/Roots Platter 资料补充 5 个小节：经验和等级、分阶段升级路线、刷法和效率、装备和食物、职业选择。
- 补充明确事实：战斗经验来自击杀怪物，农场怪物只给标准经验 1/3，读《战斗季刊》或《星之书》给 250 战斗经验，技能 1-10 级总经验阈值，战斗等级对生命值和武器体力消耗的影响。
- 补充分阶段路线：第 5 天矿井开放后从 1-40 层起步，40-79 层刷煤尘精灵/冰霜蝙蝠/材料，80-120 层和骷髅洞穴提高经验效率，姜岛火山用于后期补战斗等级和材料。
- 补充效率建议：电梯反复刷怪物密集层、怪物香水、探险家公会讨伐目标、把战斗经验和材料目标绑定。
- 补充装备/料理建议：武器伤害优先于硬磨，矿井底后可买熔岩武士刀，五彩碎片优先换银河剑，块茎拼盘/香辣鳗鱼/蟹黄糕/南瓜汤/咖啡等按阶段使用。
- `StardewQueryPlannerService` prompt 增加分类规则：技能等级怎么升、快速升级、职业怎么选、五大技能经验路线归为 `SKILL`。
- AI tool 描述补充技能等级快速升级、战斗等级低怎么练、技能职业选择、战斗季刊/星之书触发词。
- 测试补充：加深 `StardewGuideServiceTest` 战斗快速升级断言，新增 `StardewGuideRetrieverTest` typed `SKILL` 检索测试，新增 `StardewQueryPlannerServiceTest` SKILL intent 解析测试。
- 检索框架收口：`StardewGuideService` 继续保留为本地知识库查询和证据格式化层，但 `StardewGuideAssistantService`/`StardewGuideRetriever` 不再在证据缺失或 `UNKNOWN` intent 时回退到旧的 `answer(String)` 自由判断链路。
- AI 规划失败或 JSON 无效时，`StardewQueryPlannerService` 只生成一个很薄的本地 typed fallback plan；兜底也保持“分类 -> 关键词 -> typed 检索”，避免重新陷入旧关键词命中地狱。
- 新增 UNKNOWN 边界测试：当 AI 明确返回 `UNKNOWN` 时，`鸡舍升级材料` 不会被旧自由文本路由误答成建筑升级；当 AI 挂掉时，`斧头升级需要什么` 仍通过 typed TOOL fallback 查到工具升级资料。

本轮聚焦验证结果：

- 首次执行 Stardew 相关测试时发现本地 fallback 将“矮人卷轴在哪刷”误判为居民位置；已修正为博物馆/卷轴优先归 `MUSEUM`，并排除“在哪刷”触发居民日程。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：112 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 钓鱼技能快速升级攻略加厚：

- 针对“钓鱼等级低怎么快速升级/钓鱼职业怎么选/钓鱼工具怎么搭配”补强本地结构化 `fishing_skill` 攻略。
- 按官方 Fishing/Skills/Fishing Rod/Bait And Bobber/Seafoam Pudding 资料补充 5 个小节：经验和等级、分阶段升级路线、效率技巧、装备和食物、职业选择。
- 补充明确事实：钓鱼经验来自鱼竿、蟹笼、鱼塘和技能书；非鱼物品 3 XP，蟹笼 5 XP，读《鱼饵和浮标》或《星之书》250 XP，宝箱经验 x2.2，完美钓鱼经验 x2.4，传说鱼经验 x5。
- 补充分阶段路线：0-2 级用训练用鱼竿稳成功率，2 级玻璃纤维鱼竿 + 鱼饵提高上钩次数，3 级蟹笼补低操作经验，6 级铱金鱼竿配钓具，9 级海泡布丁挑战高难鱼。
- 补充效率建议：气泡点咬钩约快 4 倍，远离岸边减少垃圾并提高鱼尺寸/品质，普通鱼饵/高级鱼饵减少等待时间，软木塞/陷阱浮标和加钓鱼料理降低小游戏压力。
- 本地 fallback 分类器补充“钓鱼 + 等级/升级/经验/怎么练/快速/职业”归 `SKILL`，避免 AI 不可用时把“钓鱼等级低”误归为 `FISH` 鱼列表。
- 测试补充：新增 `StardewGuideServiceTest` 钓鱼快速升级断言，新增 `StardewGuideRetrieverTest` typed `SKILL` 检索测试并断言不返回夏季鱼类，新增 `StardewQueryPlannerServiceTest` 本地 fallback 分类测试。

本轮聚焦验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest`：82 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：115 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 采矿技能快速升级攻略加厚：

- 针对“采矿等级低怎么快速升级/挖矿技能怎么练/采矿职业怎么选”补强本地结构化 `mining_skill` 攻略。
- 按官方 Mining/Skills/The Mines/Skull Cavern/Pickaxes 资料补充 5 个小节：经验和等级、分阶段升级路线、效率技巧、装备和食物、职业选择。
- 纠正旧资料错误：用镐子或炸弹破坏岩石/矿点都给采矿经验，但怪物破坏岩石不给经验。
- 补充明确事实：读《采矿月刊》或《星之书》给 250 采矿经验，淘盘每个铜/铁/金/铱矿石 1 XP，铜矿点 5、铁矿点 12、金矿点 18、铱矿点 50、万象晶球节点 64、钻石节点/神秘石 150。
- 补充分阶段路线：春 5 矿井开放后先推每 5 层电梯；1-39 层铜矿，40-79 层铁矿/煤尘精灵，80-120 层金矿/岩浆晶球/头骨钥匙，骷髅洞穴用炸弹、楼梯、好运日和速度/运气食物刷深层。
- 补充效率建议：普通石头在矿井里通常不给额外经验，优先矿点/晶球节点/宝石节点；推层数和刷资源要分目标；背包、食物、炸弹、楼梯比硬敲普通石头更关键。
- 测试补充：新增 `StardewGuideServiceTest` 采矿快速升级断言，新增 `StardewGuideRetrieverTest` typed `SKILL` 检索测试并断言不跑到鸡舍/鱼类，新增 `StardewQueryPlannerServiceTest` 本地 fallback 分类测试。

本轮聚焦验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest`：85 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：118 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 耕种技能快速升级攻略加厚：

- 针对“耕种等级低怎么快速升级/耕种职业怎么选/洒水器前怎么练”补强本地结构化 `farming_skill` 攻略。
- 按官方 Farming/Skills/Crops/Animals/Stardew Valley Almanac 资料补充 5 个小节：经验和等级、分阶段升级路线、效率技巧、装备和食物、职业选择。
- 补充明确事实：作物经验因作物而异，多次收获每次给经验，但蓝莓/蔓越莓/土豆这类一次多产只按第一份给经验；动物互动/拾取产品各 5 XP；松露给觅食经验；年鉴/星之书 250 XP；锄地/浇水不给经验。
- 补充分阶段路线：0-2 级用防风草/土豆/花椰菜起步；春季花椰菜和草莓，夏季蓝莓/甜瓜，秋季蔓越莓/南瓜；洒水器成型后扩大规模。
- 补充效率建议：耕种等级低的核心是提高可收获格子数，肥料影响品质和售价但不直接加经验；作物成熟当天才给经验，月底避免种来不及成熟作物。
- 测试补充：新增 `StardewGuideServiceTest` 耕种快速升级断言，新增 `StardewGuideRetrieverTest` typed `SKILL` 检索测试并断言不跑到作物/鱼类列表，新增 `StardewQueryPlannerServiceTest` 本地 fallback 分类测试。

本轮聚焦验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest`：88 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：121 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 觅食技能快速升级攻略加厚：

- 针对“觅食等级低怎么快速升级/觅食职业怎么选/冬季怎么补觅食经验”补强本地结构化 `foraging_skill` 攻略。
- 按官方 Foraging/Skills/Secret Woods/Trees/Woodcutter's Weekly/Pancakes/Survival Burger/Tropical Curry 资料补充 5 个小节：经验和等级、分阶段升级路线、效率技巧、装备和食物、职业选择。
- 补充明确事实：普通地面采集物 7 XP、春葱 3 XP、野种子产物 2 觅食 XP + 3 耕种 XP；砍树 14 XP、树桩 2 XP、树枝/苔藓 1 XP、大型树桩/大圆木 25 XP；技能书 250 XP。
- 补充不计经验边界：炸弹炸倒树不给觅食经验，怪物掉落采集物、花盆物品、矿井锄出的洞穴胡萝卜等不按普通拾取给觅食经验。
- 补充分阶段路线：前期跑图捡采集物和春葱并砍树；鲑莓季/黑莓季摇灌木丛；钢斧后每天秘密森林 6 个大型树桩；冬季用冬季野种子补等级。
- 补充效率和装备建议：周日/换季清理前别漏采集物，树肥可做木材和经验林，煎饼/觅食汉堡/热带咖喱按阶段临时提高觅食等级。
- 测试补充：新增 `StardewGuideServiceTest` 觅食快速升级断言，新增 `StardewGuideRetrieverTest` typed `SKILL` 检索测试并断言不跑到作物/鱼类/资源列表，新增 `StardewQueryPlannerServiceTest` 本地 fallback 分类测试。

本轮聚焦验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest`：91 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：124 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 技能书/力量书/书商攻略加厚：

- 针对“价格目录有什么用/星之书有什么用/酱料女皇食谱在哪里买/技能书值得买吗”等 1.6 书籍问题补强 `skill_books` 与 `bookseller` 数据。
- 按官方 Bookseller/Books/Book Of Stars/Price Catalogue/Queen Of Sauce Cookbook 资料补充 5 个小节：书商和读书规则、技能经验书、固定出售和高优先级、常见力量书效果、购买建议。
- 补充书商 stock：技能书、星之书、价格目录、风之道第一卷/第二卷、马之书、老滑腿、酱料女皇食谱，支持“在哪里买/多少钱/解锁条件”类检索。
- 补充明确事实：单项技能书给 250 对应技能经验，星之书给所有技能各 250 经验，满级后给 1,125 精通点；价格目录显示售价；酱料女皇食谱需要 100 金核桃后固定出售。
- 路由修复：新增书籍问题 fallback 分类，书籍效果/值得买吗走 `GUIDE`，明确购买问题走 `SHOP`；同时修复“价格目录”自带“价格”导致误判商店的问题。
- 路由修复：新增 `skill_books` guide-over-shop / guide-over-cooking 保护，避免“价格目录有什么用”被商品命中抢走，也避免“酱料女皇食谱怎么解锁”被普通料理列表抢走。
- AI tool 描述补充价格目录、风之道、马之书、酱料女皇食谱、力量书效果/购买触发词。
- 测试补充：新增 `StardewGuideServiceTest` 书籍效果断言，新增 `StardewGuideRetrieverTest` typed `GUIDE`/`SHOP` 证据测试，新增 `StardewQueryPlannerServiceTest` fallback 分类测试。

本轮聚焦验证结果：

- 首次聚焦测试暴露两个真实路由问题：`价格目录有什么用` 被 `价格` 字样误归 `SHOP`，且 raw query 被书商商品命中抢走；已通过购买意图收窄和 guide-over-shop 修复。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest`：96 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：129 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 料理/饮料 buff 机制攻略加厚：

- 针对“料理 buff 怎么叠加/姜汁汽水和咖啡能不能一起/骷髅洞穴吃什么 buff 好/钓难鱼吃什么”等问题补强 `skill_food_buffs`。
- 按官方 Food/Buffs/Spicy Eel/Magic Rock Candy/Seafoam Pudding 资料补充 5 个小节：叠加和覆盖规则、骷髅洞穴和火山、钓鱼和收集包、技能和日常效率、选择思路。
- 补充明确机制：同一时间通常只能有 1 组食物 buff 和 1 组饮料 buff；新食物覆盖旧食物，新饮料覆盖旧饮料；无 buff 回复食物不会清掉已有 buff。
- 补充叠加边界：速度、运气和最大体力可以通过食物 + 饮料叠加；姜汁汽水可叠幸运午餐/南瓜汤，但会覆盖咖啡/三倍浓缩咖啡。
- 补充场景建议：骷髅洞穴默认香辣鳗鱼或幸运午餐 + 三倍浓缩咖啡；战斗压力大用南瓜汤/蟹黄糕；高端冲刺用魔法糖冰棍；钓难鱼用海泡布丁。
- 路由修复：`skill_food_buffs` 新增 guide-over-cooking 保护，叠加/覆盖/机制类问题走 `GUIDE`；“骷髅洞穴吃什么料理 buff 好”仍保留 `COOKING` 推荐列表。
- 测试补充：新增 `StardewGuideServiceTest` buff 叠加机制断言，新增 `StardewGuideRetrieverTest` typed `GUIDE`/`COOKING` 分流测试，新增 `StardewQueryPlannerServiceTest` fallback 分类测试。

本轮聚焦验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：134 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 基础料理/普通回复料理结构化补齐：

- 料理/饮品本地结构化条目从 19 道扩展到 46 道，新增 27 道基础/普通/早期常问料理。
- 新增条目覆盖：煎蛋、煎蛋卷、沙拉、奶酪花椰菜、烤鱼、防风草汤、蔬菜杂烩、全套早餐、炸鱿鱼、奇怪的小面包、炒蘑菇、披萨、豆类火锅、琉璃山药、鲤鱼惊喜、薯饼、薄煎饼、鲑鱼晚餐、鱼肉卷、香酥鲈鱼、面包、椰汁汤、鳟鱼汤、巧克力蛋糕、生鱼片、寿司卷、玉米饼。
- 类型内检索补强：`COOKING` 下新增回血/回复/普通料理、前期/基础、材料菜/中间材料等 tag 过滤，支持“前期回血普通料理有哪些”。
- fallback 分类补强：当 AI 不可用时，“巧克力蛋糕怎么做/鳟鱼汤材料”等菜名形态 + 动作词会归到 `COOKING`，不落到 `UNKNOWN`。
- typed 检索修复：`StardewGuideRetriever` 给 COOKING 关键词补“料理”后，具体菜谱问题仍按“怎么做/材料/效果/来源”优先返回 `cooking_recipe`，不被泛料理列表抢走。
- 测试补充：新增 cooking id 覆盖测试、普通料理详情测试、回血料理列表不跑偏测试、typed COOKING 具体菜谱检索测试、fallback 菜名分类测试。

本轮聚焦验证结果：

- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest,StardewKnowledgeRepositoryTest`：128 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：139 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 常用商店/兑换商人结构化补齐：

- 本地商店/商人数据从 9 个扩展到 15 个。
- 新增 6 个发布版常用商人入口：冒险家公会、矮人商店、绿洲、姜岛商人、齐先生核桃房、星之果实餐吧。
- 新增常问商品/兑换覆盖：熔岩武士刀、银河武器、讨伐戒指、物品恢复、炸弹/超级炸弹、生命药水、杨桃种子、大黄种子、甜菜种子、高级生长激素、香蕉树苗、芒果树苗、菠萝种子、豪华保湿土壤配方、马笛、城镇钥匙、压力喷嘴、银河之魂、咖啡、沙拉、披萨、三倍浓缩咖啡配方等。
- `StardewQueryPlannerService` 的本地 fallback 增加商店名识别保护：`冒险家公会几点开`、`齐钻商店卖什么` 这类商店营业/库存问题归 `SHOP`，不被“几点/在哪”误判成 `VILLAGER_SCHEDULE`。
- 测试补充：
  - 仓库测试强制校验 15 个常用商店 id 全部存在。
  - `answerEvidence(SHOP, ...)` 覆盖冒险家公会营业、熔岩武士刀、炸弹、杨桃种子、香蕉树苗、马笛、咖啡等 typed 证据查询。
  - `StardewGuideRetrieverTest` 覆盖多关键词 SHOP plan，确认不会串到鱼类、工具、建筑或果树路线。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时的商店名/兑换商店 fallback 分类。

本轮聚焦验证结果：

- JSON 轻量校验：`shops=15`，关键新增 id 已全部加载。
- `git diff --check`：无 whitespace 问题。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest,StardewKnowledgeRepositoryTest`：134 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：145 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 特殊商店/后期商人结构化补齐：

- 本地商店/商人数据从 15 个扩展到 23 个。
- 新增 8 个发布版特殊购买/兑换入口：哈维的诊所、JojaMart、赌场、火山矮人商店、法师塔、冰淇淋摊、废弃屋帽子店、浣熊商店。
- 新增常问商品/兑换覆盖：体力药、肌肉回复药、Joja 会员和社区项目、自动抚摸机、赌场齐币兑换/稀有稻草人/传送图腾、火山矮人岛屿传送图腾配方和炸弹、法师塔幻觉神龛/方尖塔/黄金钟/祝尼魔小屋、冰淇淋、成就帽子、浣熊兑换种子和浣熊日志等。
- `StardewQueryPlannerService` 的本地 fallback 扩展特殊商店名识别，支持 `赌场怎么进`、`浣熊商店怎么解锁`、`Joja 卖什么`、`法师塔幻觉神龛多少钱` 等问题归入 `SHOP`，不被居民位置、建筑或资源路线抢走。
- `stardew_guide` tool 描述同步补充 Joja、赌场、法师塔、火山矮人、浣熊商店等范围。
- 测试补充：
  - 仓库测试强制校验 23 个商店 id 全部存在。
  - `answerEvidence(SHOP, ...)` 覆盖体力药、自动抚摸机、赌场进入、岛屿图腾配方、幻觉神龛、冰淇淋、帽子、胡萝卜种子等 typed 证据查询。
  - `StardewGuideRetrieverTest` 覆盖特殊商店多关键词 SHOP plan，确认不会串到鱼类、工具、果树或居民位置路线。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时的赌场/浣熊商店解锁 fallback 分类。

本轮聚焦验证结果：

- JSON 轻量校验：`shops=23`，重复 id 为空，关键新增 id 无缺失。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideServiceTest,StardewKnowledgeRepositoryTest,StardewGuideToolTest`：138 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：148 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 节日商店结构化补齐：

- 本地商店/商人数据从 23 个扩展到 29 个。
- 新增 6 个发布版节日购买/兑换入口：复活节商店、花舞节商店、月光水母节商店、星露谷展览会商店、万灵节商店、沙漠节商店。
- 新增常问商品/兑换覆盖：草莓种子、花桶配方、稀有稻草人 #1/#2/#5、海泡布丁、星之果实、沙漠节卡利科三花蛋兑换、沙漠节魔法糖冰棍、神秘盒、超级炸弹、草莓种子等。
- 修复商店价格格式：星星币、卡利科三花蛋等非金币货币现在展示为 `2,000 星星币`、`250 卡利科三花蛋`，不再误格式化成金币。
- `StardewQueryPlannerService` 的本地 fallback 扩展节日商店名识别，支持 `沙漠节换什么`、`草莓种子在哪里买`、`万灵节稀有稻草人多少钱` 等问题归入 `SHOP`。
- `stardew_guide` tool 描述同步补充节日商店、沙漠节商店范围。
- 测试补充：
  - 仓库测试强制校验 29 个商店 id，并覆盖 6 个新增节日商店 id。
  - `answerEvidence(SHOP, ...)` 覆盖草莓种子、沙漠节兑换、展览会星之果实、月光水母节海泡布丁、万灵节稀有稻草人等 typed 证据查询。
  - `StardewGuideRetrieverTest` 覆盖节日商店多关键词 SHOP plan，确认不会串到鱼类、工具、居民位置或建筑路线。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时的节日商店 fallback 分类。

本轮聚焦验证结果：

- JSON 轻量校验：`shops=29`，重复 id 为空，6 个节日商店 id 已全部加载。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：155 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 书籍/力量书/技能书结构化检索补齐：

- 新增 `books` 顶层数据和 `BookGuide` 模型，将此前“技能书与书商”泛攻略拆出精确书籍证据层。
- 本地书籍数据补齐 26 本：19 本力量书、5 本单项技能书、星之书、酱料女皇食谱。
- `StardewKnowledgeRepository` 新增 `books()`、`findBook()`、`findBooks()`，按书名和别名做精确检索。
- `StardewGuideService` 在 GUIDE typed evidence 中优先返回书籍详情；多本书同问返回书籍对照；SHOP typed evidence 问书籍来源时也可返回书籍获取证据，避免不在旧商店库存里的书直接落 Wiki。
- 测试补充：
  - 仓库测试强制校验 26 本书 id、效果、获取方式和来源不为空。
  - `StardewGuideServiceTest` 覆盖价格目录、星之书、酱料女皇食谱、怪物图鉴、战斗季刊和多本书同问。
  - `StardewGuideRetrieverTest` 覆盖 GUIDE/SHOP typed plan 下书籍证据不串到鱼类、工具、建筑、料理或居民位置。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时书籍效果归 GUIDE、书籍购买/来源归 SHOP。

本轮聚焦验证结果：

- JSON 轻量校验：`books=26`，重复 id 为空，关键新增 id 无缺失。
- `git diff --check`：无 whitespace 问题。
- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest,StardewQueryPlannerServiceTest`：142 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：153 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 火山锻造/附魔攻略结构化补齐：

- 本地通用攻略从 35 项扩到 36 项，新增 `forge_enchanting`：火山锻造与附魔。
- 覆盖内容包括锻造台位置和用途、武器宝石锻造成本与宝石效果、武器/工具附魔成本与推荐、1.6 先天附魔、银河之魂和无限武器、戒指合成/拆解。
- 检索框架调整方向：继续让 `StardewQueryPlannerService` 负责分类，火山锻造台、武器锻造、工具附魔、武器附魔、戒指合成、无限武器、银河之魂统一归 `GUIDE`；`StardewGuideService` 只在 typed evidence 路径下提供本地证据。
- 兼容保护：旧 `answer(String)` 入口遇到 `forge_enchanting` 也优先返回 guide，不被“工具升级”或“戒指制作材料”抢答。
- 测试补充：
  - 仓库测试强制校验 `forge_enchanting` 存在、核心章节齐全且有 Forge 来源。
  - `StardewGuideServiceTest` 覆盖银河剑锻造、工具附魔、无限武器/银河之魂、戒指合成。
  - `StardewGuideRetrieverTest` 覆盖 typed `GUIDE` 多关键词检索，确认不串到工具升级、作物、鱼类或商店。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时锻造/附魔 fallback 分类归 `GUIDE`。

本轮验证结果：

- JSON 轻量校验：`guides=36`，`forge_enchanting=true`，重复 id 为空。
- `git diff --check`：无 whitespace 问题。
- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest,StardewQueryPlannerServiceTest,StardewGuideToolTest`：152 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：162 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 精通系统攻略加厚：

- 在现有 `mastery` 通用攻略上加厚，不新增旁路命令；继续保持“AI 分类 -> 关键词检索 -> 证据整合 -> 自然回复”主链路。
- 按官方 Mastery/Iridium Scythe/Advanced Iridium Rod/Challenge Bait/Anvil/Mini-Forge 等页面补齐：精通洞穴解锁、精通点来源、五级花费、五系精通奖励、关键配方材料、奖励细节和优先级建议。
- 精通点规则补充：五项技能 10 级后进入统一精通条；第 1-5 个精通分别要 10,000 / 15,000 / 20,000 / 25,000 / 30,000 点，合计 100,000；耕种经验按 50% 转精通点，其余技能按 100%。
- 奖励覆盖：铱金镰刀、祝福雕像、金色动物饼干、矮人之王雕像、重型熔炉、神秘树种子、宝藏图腾、高级铱金鱼竿、挑战鱼饵、金色钓鱼宝箱、铁砧、迷你锻造台和小饰品。
- 路由保护：`StardewQueryPlannerService` fallback 将精通奖励名和“精通先选哪个/精通点怎么刷”归为 `GUIDE`，避免 `高级铱金鱼竿怎么获得` 被鱼类或资源路径抢答；`StardewGuideRetriever` 对 GUIDE 精通问题补 `精通系统` hint。
- 测试补充：
  - `StardewGuideServiceTest` 覆盖精通优先级、高级铱金鱼竿、挑战鱼饵、铁砧和小饰品。
  - `StardewGuideRetrieverTest` 覆盖 typed `GUIDE` 多关键词检索，确认不串到鱼类、机器或资源。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时精通奖励问题 fallback 分类归 `GUIDE`。

本轮验证结果：

- JSON 轻量校验：`guides=36`，`masterySections=5`，`masterySources=13`，重复 id 为空。
- `git diff --check`：无 whitespace 问题。
- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest,StardewQueryPlannerServiceTest,StardewGuideToolTest`：155 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：165 tests, 0 failures。
- `-pl bb-bot-server -am -DskipTests compile`：BUILD SUCCESS。

2026-06-26 小饰品与铁砧重铸攻略补齐：

- 本地通用攻略从 36 项扩到 37 项，新增 `combat_trinkets`：小饰品与铁砧重铸。
- 按官方 Trinkets/Anvil/中文饰品/中文铁砧页面补齐：战斗精通解锁、获取方式、骷髅洞穴宝箱/怪物/容器掉落规则、窃贼戒指和怪物图鉴不影响饰品掉落、铁砧材料与重铸消耗、全部 8 个小饰品效果与可重铸数值。
- 玩法建议补齐：仙女盒/寒冰法杖偏保命控场，魔法箭筒偏输出，鹦鹉蛋偏刷钱，青蛙蛋不适合补掉落/任务/怪物根除。
- 路由保护：
  - `StardewQueryPlannerService` fallback 新增 `looksLikeTrinketQuery`，小饰品、铁砧重铸、仙女盒、青蛙蛋、寒冰法杖、魔法箭筒、鹦鹉蛋、蜥怪的爪子、魔法发胶归 `GUIDE`。
  - `StardewGuideRetriever` 遇到饰品类 GUIDE 查询补 `饰品攻略` hint，避免再被 `mastery` 精通总览抢答。
  - `StardewGuideTool` 描述补齐小饰品/铁砧重铸类问题，继续要求命令入口和 AI tool 走同一套分类检索整合路径。
- 测试补充：
  - `StardewKnowledgeRepositoryTest` 校验 `combat_trinkets` 存在、分类为 `combat`、核心别名/章节/来源齐全。
  - `StardewGuideServiceTest` 覆盖小饰品哪个好、青蛙蛋是否适合刷掉落、魔法箭筒重铸词条、鹦鹉蛋最高等级收入门槛。
  - `StardewGuideRetrieverTest` 覆盖 typed `GUIDE` 饰品多关键词检索，确认不串到精通总览、资源或工具升级。
  - `StardewQueryPlannerServiceTest` 覆盖 AI 不可用时饰品和铁砧类问题 fallback 分类归 `GUIDE`。

本轮验证结果：

- JSON 轻量校验：`guides=37`，`combat_trinkets` 5 个章节、4 个来源，重复 id 为空；`mastery` 不再保留 `小饰品/铁砧` 弱别名，`combat_trinkets` 保留精确 `铁砧` 别名。
- `git diff --check`：无 whitespace 问题。
- 首次 focused tests 暴露 `魔法箭筒铁砧刷什么词条` 被 `mastery` 抢答；修复方式是调整数据归属，移除 `mastery` 的 `小饰品/铁砧` 弱别名，而不是放宽断言。
- `StardewKnowledgeRepositoryTest,StardewGuideServiceTest,StardewGuideRetrieverTest,StardewQueryPlannerServiceTest`：158 tests, 0 failures。
- `StardewQueryPlannerServiceTest,StardewGuideRetrieverTest,StardewGuideAssistantServiceTest,StardewGuideServiceTest,StardewGuideToolTest,BbStardewHandlerTest,StardewWikiApiClientTest,StardewKnowledgeRepositoryTest`：169 tests, 0 failures。
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
- 尚未覆盖全量数据质量测试，例如全部鱼完整收集、全部作物、全部建筑、全部技能、剩余制作/怪物/矿物条目和重混收集包全量逐项校对。
- 居民已做到 34 位送礼资料和首批结构化日程全覆盖；节日、姜岛度假、婚后、好感剧情、绿雨、沙漠节等复杂覆盖规则还未逐条精确建模。
- 商店已覆盖 29 个常用/特殊/节日购买兑换入口；剩余节日全装饰随机库存、沙漠节全村民商铺、全量旅行货车随机池、全帽子/全赌场装饰/全浣熊兑换细表等后续继续补齐。
- 建筑已扩到 27 个核心/后期条目，已覆盖方尖塔、黄金钟、祝尼魔小屋、潘姆房屋和城镇捷径社区升级；多人小屋不同样式、宠物碗、岛屿农舍、温室、特殊场景建筑等仍未完整结构化。
- 工具升级已结构化首批 6 类工具，鱼竿/鱼饵/浮标搭配已在钓鱼技能攻略中补一版高频路线；火山锻造、武器/工具附魔、无限武器和戒指合成已补一版攻略；但特殊工具、全部钓具数值、全部附魔概率/武器数值和后期搭配还未完整结构化。
- 机器/加工/制作设备/常用 craftable 已扩到 80 个核心条目，已补肥料、图腾、戒指、怪物香水、仙尘、鱼饵、钓具、蟹笼；但重型树液采集器、虫饵盒、豪华虫饵盒、木材削片机、地板/围栏/照明/装饰、后期精通设备和全制作清单还未完整结构化。
- 资源获取已扩到 91 项，新增一批博物馆、古物、矿物、晶球、稀有物品、动物产品、果树水果和首批高频怪物掉落；但全 42 件古物、全 53 件矿物、全怪物掉落表、全姜岛/火山材料、全鱼塘产物和特殊货币还未完整结构化。
- 技能攻略目前覆盖五大基础技能、战斗/钓鱼/采矿/耕种/觅食快速升级细分路线、精通系统奖励/配方/优先级、职业重置、首批料理/饮料 buff 数值和叠加规则；料理数据已扩到 83 道，书籍/力量书/技能书已补 26 本结构化详情，火山锻造/附魔已补一版通用攻略，小饰品/铁砧重铸已补全数值和高频建议；后续还需继续补技能相关装备、全部附魔概率/武器数值和更细的场景化路线。

## 后续补齐方向

1. 数据覆盖：
   - 全鱼类剩余补齐：继续核对全鱼表、魔法鱼饵提示和全鱼收集策略；虾虎鱼、三种果冻已补
   - 全收集包逐项校对；普通社区中心、房间概览、失踪收集包和首批 21 个重混/随机收集包已结构化，后续继续核对是否还有遗漏的重混变体、重混选择规则和旅行货车可购性提示
   - 居民复杂覆盖规则：节日、姜岛度假、婚后、好感剧情、绿雨、沙漠节等；生日、首批礼物偏好和普通/高频日程已覆盖 34 位可送礼居民
   - 全作物、果树、季节、成熟天数、基础收益、收集包用途；首批 30 个核心作物已结构化
   - 全建筑、房屋升级、动物、机器；首批核心农场建筑、房屋升级、后期魔法建筑/社区升级和 80 个核心机器/制作设备/常用 craftable 已结构化，后续补多人小屋样式、宠物碗、岛屿农舍、温室等特殊建筑和全部机器设备
   - 全工具、鱼竿、附魔、锻造、特殊工具和使用路线；首批工具升级费用/材料已结构化，火山锻造/武器和工具附魔/无限武器/戒指合成已补一版高频攻略
   - 全技能升级、职业选择、经验获取、快速升级路线、Mastery、技能书、职业重置、技能食物 buff、技能相关装备和附魔搭配；Mastery 已补五系奖励和高频配方，小饰品/铁砧重铸已补全数值，后续继续补全部附魔概率、武器数值和更细的战斗流派搭配
   - 矿井、骷髅洞穴、火山、怪物掉落、博物馆、烹饪、制作；首批 91 个高频资源获取条目已结构化，已补煤炭、太阳精华、虚空精华、蝙蝠翅膀、史莱姆泥、虫肉、骨头碎片等高频怪物掉落，后续继续补全 42 件古物、53 件矿物和完整怪物掉落表
2. 测试覆盖：
   - 每个 intent 至少 happy path + miss + alias + 条件过滤
   - Wiki 兜底 query rewriting 回归样例
   - handler/tool 输出契约
   - 数据完整性校验测试
3. 验证策略：
   - 当前阶段先不运行实际服务测试。
   - 每轮继续执行星露谷相关单元测试。
   - 每轮继续执行 `bb-bot-server` reactor compile。
