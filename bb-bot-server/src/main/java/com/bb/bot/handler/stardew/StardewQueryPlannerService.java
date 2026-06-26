package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StardewQueryPlannerService {

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StardewQueryPlannerService(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public StardewQueryPlan plan(String query) {
        String cleanedQuery = StringUtils.defaultString(query).trim();
        if (StringUtils.isBlank(cleanedQuery)) {
            return StardewQueryPlan.fallback(cleanedQuery);
        }
        String raw = chatSafely(List.of(
                ChatMessage.system("""
                        你是星露谷物语攻略查询规划器，只负责把用户问题拆成检索计划，不负责回答。
                        必须只输出 JSON，不要解释，不要 Markdown。
                        JSON 结构：
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "RESOURCE",
                              "keywords": ["恐龙蛋怎么获得"],
                              "constraints": {
                                "season": "", "location": "", "weather": "", "time": "",
                                "day": "", "weekday": "", "villager": ""
                              }
                            }
                          ]
                        }
                        type 只能从这些枚举中选择：
                        FISH, BUNDLE, VILLAGER_SCHEDULE, VILLAGER_PROFILE, RESOURCE, MONSTER_DROP, FISH_POND,
                        ANIMAL_CARE, FRUIT_TREE, CROP, TOOL, BUILDING, MACHINE, SHOP,
                        COOKING, SKILL, MUSEUM, GUIDE, UNKNOWN。
                        规划规则：
                        - 可以拆成 1-4 个 intent；组合问题要拆开，例如“动物怎么养，大壶牛奶为什么不出”拆 ANIMAL_CARE + RESOURCE。
                        - keywords 必须是适合检索的中文短句，保留动作，例如“怎么获得”“怎么做”“升级材料”“在哪里”“怎么种”。
                        - 技能等级怎么升、快速升级、职业怎么选、战斗/采矿/钓鱼/耕种/觅食经验路线归为 SKILL。
                        - 精通系统、精通点、精通先选哪个、五系精通奖励、铱金镰刀、高级铱金鱼竿、挑战鱼饵、宝藏图腾、祝福雕像归为 GUIDE。
                        - 小饰品、饰品、铁砧重铸、仙女盒、青蛙蛋、寒冰法杖、魔法箭筒、鹦鹉蛋、蜥怪的爪子、魔法发胶归为 GUIDE。
                        - 火山锻造台、武器锻造、工具附魔、武器附魔、戒指合成、无限武器、银河之魂怎么用归为 GUIDE。
                        - 泛问特殊货币、兑换货币、节日货币有哪些/怎么规划，例如“特殊货币有哪些”“各种币怎么花”，归为 GUIDE。
                        - 具体特殊货币怎么获得/怎么用/换什么，例如“齐钻怎么获得”“金核桃怎么用”“三花蛋换什么”“金色标签怎么获得”，归为 RESOURCE。
                        - 岛屿办事处、姜岛化石、蜗牛教授、紫花、紫海星、化石捐赠顺序/奖励，归为 GUIDE。
                        - 具体姜岛办事处化石或火山/姜岛资源怎么获得，例如“蛇头骨怎么获得”“蛇椎骨哪里刷”“木乃伊蝙蝠哪里刷”“金色椰子怎么开”“生姜怎么获得”“岩浆菇哪里找”，归为 RESOURCE。
                        - 具体矿物/宝石怎么获得、哪里找、开哪个晶球，例如“黄水晶哪里找”“大理石怎么获得”“陶瓷碎片开哪个晶球”，归为 RESOURCE。
                        - 具体古物/文物怎么获得、哪里刷、谁掉落、开不开古物宝藏，例如“古代玩偶怎么获得”“矮人卷轴 II 哪里刷”“诡异玩偶黄怎么拿”，归为 RESOURCE。
                        - 问某个怪物掉什么、在哪刷、楼层、战斗经验、怪物掉落表，例如“煤尘精灵掉什么”“飞蛇在哪刷”“熔岩潜伏怪掉落”，归为 MONSTER_DROP。
                        - 问鱼塘养什么好、某种鱼鱼塘产什么、鱼塘扩容任务、鱼籽/鱼籽酱、鱼塘要什么任务物品，例如“鲟鱼鱼塘产什么”“岩浆鳗鱼鱼塘要什么”“鱼塘养什么好”，归为 FISH_POND。
                        - 问鱼塘建筑本身的建造材料、价格、占地、罗宾建造，例如“鱼塘建造材料多少钱”，归为 BUILDING。
                        - 问某个物品怎么获得/哪里刷，例如“虚空精华哪里刷”“蝙蝠翅膀怎么获得”，仍归为 RESOURCE。
                        - 博物馆整体补全、缺古物/缺矿物路线、全套捐赠奖励，归为 MUSEUM。
                        - 保留季节、地点、天气、时间、居民名、物品名、建筑名、收集包名。
                        - 缺少居民位置查询必需的游戏内时间时，needMoreInfo=true，并给 clarificationQuestion。
                        - 不要声称读取存档或当前真实游戏状态。
                        """),
                ChatMessage.user(cleanedQuery)
        ), ModelTier.LIGHT);
        StardewQueryPlan plan = parsePlan(raw);
        if (plan == null || plan.getIntents() == null || plan.getIntents().isEmpty()) {
            return localFallbackPlan(cleanedQuery);
        }
        for (StardewQueryPlan.PlannedIntent intent : plan.getIntents()) {
            if (intent.getType() == null) {
                String keyword = intent.getKeywords() == null || intent.getKeywords().isEmpty()
                        ? cleanedQuery
                        : intent.getKeywords().get(0);
                intent.setType(inferIntent(keyword));
            }
            if (intent.getKeywords() == null || intent.getKeywords().isEmpty()) {
                intent.setKeywords(List.of(cleanedQuery));
            }
            if (intent.getConstraints() == null) {
                intent.setConstraints(new StardewQueryPlan.StardewQueryConstraints());
            }
        }
        return plan;
    }

    private StardewQueryPlan localFallbackPlan(String query) {
        StardewQueryPlan plan = new StardewQueryPlan();
        StardewQueryPlan.PlannedIntent intent = new StardewQueryPlan.PlannedIntent();
        intent.setType(inferIntent(query));
        intent.setKeywords(List.of(query));
        plan.setIntents(List.of(intent));
        return plan;
    }

    private StardewGuideIntent inferIntent(String query) {
        String q = StringUtils.defaultString(query).trim();
        if (StringUtils.isBlank(q)) {
            return StardewGuideIntent.UNKNOWN;
        }
        if (containsAny(q, "收集包", "献祭", "社区中心", "电影院", "失踪的包")) {
            return StardewGuideIntent.BUNDLE;
        }
        if (looksLikeTrinketQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeMasteryQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeSpecificCurrencyResourceQuery(q)) {
            return StardewGuideIntent.RESOURCE;
        }
        if (looksLikeSpecialCurrencyGuideQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeIslandFieldOfficeResourceQuery(q)) {
            return StardewGuideIntent.RESOURCE;
        }
        if (looksLikeIslandFieldOfficeGuideQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeForgeEnchantingQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeSpecificArtifactResourceQuery(q)) {
            return StardewGuideIntent.RESOURCE;
        }
        if (looksLikeSpecificMineralResourceQuery(q)) {
            return StardewGuideIntent.RESOURCE;
        }
        if (looksLikeMonsterDropQuery(q)) {
            return StardewGuideIntent.MONSTER_DROP;
        }
        if (looksLikeFishPondQuery(q) && !looksLikeFishPondBuildingQuery(q)) {
            return StardewGuideIntent.FISH_POND;
        }
        if (containsAny(q, "博物馆", "捐赠", "古物", "矿物", "卷轴")) {
            return StardewGuideIntent.MUSEUM;
        }
        if (looksLikeShopNameQuery(q) && containsAny(q, "几点", "营业", "开门", "关门", "卖什么", "买", "购买", "兑换", "换什么", "在哪里买", "怎么进", "解锁", "怎么解锁")) {
            return StardewGuideIntent.SHOP;
        }
        if (containsAny(q, "在哪", "位置", "日程", "行程", "几点")
                && !containsAny(q, "哪里买", "在哪里买", "怎么获得", "哪里刷", "在哪刷")) {
            return StardewGuideIntent.VILLAGER_SCHEDULE;
        }
        if (containsAny(q, "喜欢", "讨厌", "礼物", "生日", "红心", "好感")) {
            return StardewGuideIntent.VILLAGER_PROFILE;
        }
        if (containsAny(q, "斧头", "镐", "锄头", "水壶", "垃圾桶", "工具升级")
                && containsAny(q, "升级", "多少钱", "材料", "需要", "条件")) {
            return StardewGuideIntent.TOOL;
        }
        if (containsAny(q, "鸡舍", "畜棚", "筒仓", "马厩", "鱼塘", "史莱姆屋", "方尖塔", "黄金钟", "房屋升级", "社区升级", "罗宾")
                || (containsAny(q, "建筑", "建造") && containsAny(q, "材料", "需要", "多少钱", "升级"))) {
            return StardewGuideIntent.BUILDING;
        }
        if (containsAny(q, "技能", "战斗", "采矿", "耕种", "觅食", "钓鱼")
                && containsAny(q, "等级", "升级", "经验", "怎么练", "快速", "职业")) {
            return StardewGuideIntent.SKILL;
        }
        if (looksLikeBookQuery(q) && containsAny(q, "在哪里买", "哪里买", "谁卖", "多少钱", "购买")) {
            return StardewGuideIntent.SHOP;
        }
        if (looksLikeBookQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (containsAny(q, "果树", "树苗", "温室怎么种")) {
            return StardewGuideIntent.FRUIT_TREE;
        }
        if (containsAny(q, "动物", "奶牛", "山羊", "鸡", "鸭", "兔子", "恐龙", "猪", "心情", "大壶奶")) {
            return StardewGuideIntent.ANIMAL_CARE;
        }
        if (containsAny(q, "buff", "增益", "料理", "食物", "饮料")
                && containsAny(q, "叠加", "覆盖", "规则", "机制", "能一起", "能同时", "互相覆盖")) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeCookingFoodQuery(q) && containsAny(q, "怎么做", "材料", "配方", "效果", "吃什么", "有哪些")) {
            return StardewGuideIntent.COOKING;
        }
        if (containsAny(q, "料理", "食谱", "菜谱", "烹饪")) {
            return StardewGuideIntent.COOKING;
        }
        if (containsAny(q, "商店", "哪里买", "在哪里买", "谁卖", "价格", "多少钱")
                && !containsAny(q, "升级多少钱")) {
            return StardewGuideIntent.SHOP;
        }
        if (containsAny(q, "洒水器", "小桶", "罐头瓶", "蛋黄酱机", "奶酪机", "织布机", "熔炉", "避雷针", "回收机", "晶球破开器", "楼梯", "鱼饵", "浮标", "戒指", "图腾")
                && containsAny(q, "怎么做", "材料", "配方", "制作", "需要")) {
            return StardewGuideIntent.MACHINE;
        }
        if (containsAny(q, "作物", "种什么", "种子", "几天成熟", "收益", "春季", "夏季", "秋季", "冬季")
                && !containsAny(q, "钓", "鱼")) {
            return StardewGuideIntent.CROP;
        }
        if (containsAny(q, "钓", "鱼", "蟹笼", "果冻")) {
            return StardewGuideIntent.FISH;
        }
        if (containsAny(q, "怎么获得", "获取", "哪里刷", "在哪刷", "来源", "掉落", "怎么弄", "怎么拿", "哪里有")) {
            return StardewGuideIntent.RESOURCE;
        }
        if (containsAny(q, "攻略", "推荐", "路线", "怎么玩", "怎么解锁")) {
            return StardewGuideIntent.GUIDE;
        }
        return StardewGuideIntent.UNKNOWN;
    }

    private boolean looksLikeForgeEnchantingQuery(String query) {
        return containsAny(query,
                "锻造", "附魔", "火山锻造", "锻造台", "火山晶石", "银河之魂",
                "无限武器", "无限之刃", "无限之锤", "无限匕首", "戒指合成", "戒指组合",
                "forge", "enchant", "infinity weapon");
    }

    private boolean looksLikeMasteryQuery(String query) {
        return containsAny(query,
                "精通", "精通点", "精通洞穴", "精通等级", "精通奖励", "精通先选", "精通选",
                "铱金镰刀", "祝福雕像", "金色动物饼干", "矮人之王雕像", "重型熔炉",
                "神秘树种子", "宝藏图腾", "高级铱金鱼竿", "挑战鱼饵", "金色钓鱼宝箱",
                "mastery", "mastery point");
    }

    private boolean looksLikeTrinketQuery(String query) {
        return containsAny(query,
                "小饰品", "饰品", "铁砧", "重铸", "仙女盒", "青蛙蛋", "寒冰法杖",
                "黄金马刺", "魔法箭筒", "鹦鹉蛋", "蜥怪的爪子", "魔法发胶",
                "Basilisk Paw", "Fairy Box", "Frog Egg", "Ice Rod", "Golden Spur",
                "Magic Quiver", "Parrot Egg", "Magic Hair Gel", "trinket");
    }

    private boolean looksLikeSpecificCurrencyResourceQuery(String query) {
        if (!containsAny(query, "怎么获得", "获取", "哪里", "哪刷", "怎么刷", "在哪", "来源", "怎么拿",
                "怎么用", "有什么用", "换什么", "兑换", "花", "买什么", "优先")) {
            return false;
        }
        return containsAny(query,
                "齐钻", "齐先生宝石", "金核桃", "黄金核桃", "齐币", "赌场币", "星星币", "星之币",
                "奖券", "兑奖券", "三花蛋", "卡利科蛋", "火山晶石", "金色标签", "黄金标签",
                "Qi Gem", "Qi Gems", "Golden Walnut", "Golden Walnuts", "Qi Coin", "Qi Coins",
                "Star Token", "Star Tokens", "Prize Ticket", "Prize Tickets", "Calico Egg", "Calico Eggs",
                "Cinder Shard", "Cinder Shards", "Golden Tag", "Golden Tags");
    }

    private boolean looksLikeSpecialCurrencyGuideQuery(String query) {
        return containsAny(query, "特殊货币", "特殊币", "兑换货币", "节日货币", "活动货币", "各种币", "货币有哪些")
                || (containsAny(query, "齐钻", "金核桃", "齐币", "星星币", "奖券", "三花蛋", "火山晶石", "金色标签")
                && containsAny(query, "有哪些", "总览", "攻略", "规划"));
    }

    private boolean looksLikeIslandFieldOfficeResourceQuery(String query) {
        if (!containsAny(query, "怎么获得", "获取", "哪里", "哪刷", "怎么刷", "在哪", "来源", "怎么拿",
                "怎么开", "开什么", "哪里找", "掉落", "谁掉")) {
            return false;
        }
        return containsAny(query,
                "金色椰子", "黄金椰子", "金椰子",
                "生姜", "姜岛生姜",
                "岩浆菇", "岩浆帽", "火山蘑菇",
                "化石头骨", "化石脊柱", "化石尾巴", "化石腿骨", "化石肋骨",
                "蛇头骨", "蛇椎骨", "蛇脊椎",
                "木乃伊蝙蝠", "蝙蝠木乃伊", "木乃伊青蛙", "青蛙木乃伊",
                "Fossilized Skull", "Fossilized Spine", "Fossilized Tail", "Fossilized Leg", "Fossilized Ribs",
                "Snake Skull", "Snake Vertebrae", "Mummified Bat", "Mummified Frog",
                "Golden Coconut", "Ginger", "Magma Cap");
    }

    private boolean looksLikeIslandFieldOfficeGuideQuery(String query) {
        return containsAny(query,
                "岛屿办事处", "姜岛办事处", "蜗牛教授", "姜岛化石", "岛屿化石",
                "化石怎么捐", "化石捐赠", "化石奖励", "大型动物化石", "蛇化石",
                "紫花", "紫色花", "紫海星", "紫色海星",
                "Island Field Office", "Professor Snail")
                && containsAny(query, "化石", "捐", "奖励", "答案", "多少", "几个", "攻略", "办事处", "教授", "花", "海星");
    }

    private boolean looksLikeMonsterDropQuery(String query) {
        if (containsAny(query, "怪物图鉴") && !containsAny(query, "掉什么", "掉落表", "掉落")) {
            return false;
        }
        boolean asksMonsterLoot = containsAny(query, "掉什么", "掉落表", "战利品", "怪物掉落", "怪物掉落表")
                || (containsAny(query, "在哪刷", "哪里刷", "楼层", "几层", "多少经验", "战斗经验")
                && containsAny(query, "怪", "史莱姆", "蝙蝠", "幽灵", "飞蛇", "骷髅"));
        if (!asksMonsterLoot) {
            return false;
        }
        return containsAny(query,
                "史莱姆", "绿史莱姆", "蓝史莱姆", "红史莱姆", "紫史莱姆", "虎纹史莱姆", "大史莱姆",
                "蝙蝠", "冰霜蝙蝠", "熔岩蝙蝠", "铱蝠",
                "虫", "臭虫", "洞穴蝇", "蛆", "装甲虫", "变异苍蝇", "变异蛆", "装甲虫",
                "掘地虫", "煤尘精灵", "灰尘精灵", "幽灵", "碳幽灵", "腐臭幽灵", "闹鬼骷髅",
                "石魔", "岩石蟹", "熔岩蟹", "铱蟹", "木乃伊", "飞蛇", "皇家飞蛇",
                "暗影狂徒", "暗影萨满", "暗影狙击手", "骷髅", "骷髅法师", "鱿鱼娃", "蓝鱿鱼",
                "金属大头", "恐龙", "霸王喷火龙", "熔岩潜伏怪", "熔岩精灵", "熔岩火球",
                "熔岩掘地虫", "假岩浆菇", "火焰头", "矮人哨兵", "蜘蛛", "竹虫", "松露蟹",
                "荒野石魔", "铱石魔",
                "slime", "bat", "bug", "cave fly", "grub", "duggy", "dust sprite",
                "ghost", "rock crab", "lava crab", "mummy", "serpent", "shadow brute",
                "shadow shaman", "squid kid", "pepper rex", "lava lurk", "magma sprite",
                "magma sparker", "magma duggy", "hot head", "blue squid", "spider");
    }

    private boolean looksLikeFishPondQuery(String query) {
        return containsAny(query, "鱼塘", "鱼籽", "鱼子酱", "鱼籽酱", "roe", "pond")
                && containsAny(query, "产什么", "产出", "产物", "养什么", "放什么", "扩容", "任务",
                "要什么", "要啥", "需要什么", "鱼籽", "鱼子酱", "鱼籽酱", "推荐", "roe", "pond");
    }

    private boolean looksLikeFishPondBuildingQuery(String query) {
        return containsAny(query, "鱼塘")
                && containsAny(query, "建造", "建筑", "罗宾", "多少钱", "价格", "材料", "占地", "尺寸")
                && !containsAny(query, "产什么", "产出", "产物", "养什么", "放什么", "扩容", "任务", "鱼籽", "鱼子酱", "鱼籽酱");
    }

    private boolean looksLikeSpecificMineralResourceQuery(String query) {
        if (!containsAny(query, "怎么获得", "获取", "哪里", "哪刷", "在哪", "来源", "开哪个", "开什么", "晶球", "矿物")) {
            return false;
        }
        if (containsAny(query, "缺矿物", "全套矿物", "矿物怎么补", "博物馆怎么补", "博物馆缺")) {
            return false;
        }
        return containsAny(query,
                "石英", "地晶", "泪晶", "冰泪晶", "火水晶",
                "绿宝石", "海蓝宝石", "红宝石", "紫水晶", "黄水晶", "翡翠", "钻石", "五彩碎片",
                "虎眼石", "猫眼石", "赤红猫眼石", "透闪石", "黑方石", "重晶石", "青泥石",
                "方解石", "白云石", "硅钙石", "氟磷灰石", "杰明石", "日光榴石", "绿水镍矿",
                "铁铅矿", "蓝晶石", "酸性月岩", "孔雀石", "柱星叶石", "柠檬石", "新硅钙石",
                "雌黄", "石化史莱姆", "雷公蛋", "黄铁矿", "海洋石", "幽灵水晶", "碧玉",
                "天青石", "大理石", "沙岩", "花岗岩", "玄武岩", "石灰石", "皂石", "赤铁矿",
                "泥石", "黑曜石", "板岩", "精灵石", "陶瓷碎片", "星之碎片", "星星碎片",
                "emerald", "aquamarine", "ruby", "amethyst", "topaz", "jade", "diamond",
                "tigerseye", "opal", "fire opal", "alamite", "bixite", "baryte", "aerinite",
                "calcite", "dolomite", "esperite", "fluorapatite", "geminite", "helvite",
                "jamborite", "jagoite", "kyanite", "lunarite", "malachite", "neptunite",
                "lemon stone", "nekoite", "orpiment", "petrified slime", "thunder egg",
                "pyrite", "ocean stone", "ghost crystal", "jasper", "celestine", "marble",
                "sandstone", "granite", "basalt", "limestone", "soapstone", "hematite",
                "mudstone", "obsidian", "slate", "fairy stone", "star shards");
    }

    private boolean looksLikeSpecificArtifactResourceQuery(String query) {
        if (!containsAny(query, "怎么获得", "获取", "哪里", "哪刷", "在哪", "来源", "掉落", "谁掉", "开哪个", "开什么", "古物", "文物", "卷轴")) {
            return false;
        }
        if (containsAny(query, "缺古物", "全套古物", "古物怎么补", "文物怎么补", "博物馆怎么补", "博物馆缺")) {
            return false;
        }
        return containsAny(query,
                "矮人卷轴", "有缺口的土罐", "土罐", "箭头", "箭镞", "古代玩偶", "古代娃娃",
                "精灵珠宝", "精灵首饰", "咀嚼洁齿棒", "咀嚼棒", "装饰用扇子", "装饰扇",
                "恐龙蛋", "稀有圆盘", "古剑", "古代剑", "生锈的汤匙", "生锈的靴刺",
                "生锈的齿轮", "鸡雕像", "古代种子", "上古种子", "史前工具", "干海星",
                "干燥海星", "船锚", "玻璃碎片", "骨笛", "史前手斧", "矮人头盔",
                "矮人小工具", "古代鼓", "黄金面具", "黄金遗物", "诡异玩偶", "绿色诡异玩偶",
                "黄色诡异玩偶", "史前肩胛骨", "史前胫骨", "史前头骨", "手部骨骼",
                "史前肋骨", "史前脊骨", "尾部骨骼", "鹦鹉螺化石", "两栖动物化石",
                "棕榈化石", "三叶虫",
                "dwarf scroll", "chipped amphora", "arrowhead", "ancient doll", "elvish jewelry",
                "chewing stick", "ornamental fan", "dinosaur egg", "rare disc", "ancient sword",
                "rusty spoon", "rusty spur", "rusty cog", "chicken statue", "ancient seed",
                "prehistoric tool", "dried starfish", "anchor", "glass shards", "bone flute",
                "prehistoric handaxe", "dwarvish helm", "dwarf gadget", "ancient drum",
                "golden mask", "golden relic", "strange doll", "prehistoric scapula",
                "prehistoric tibia", "prehistoric skull", "skeletal hand", "prehistoric rib",
                "prehistoric vertebra", "skeletal tail", "nautilus fossil", "amphibian fossil",
                "palm fossil", "trilobite");
    }

    private boolean looksLikeBookQuery(String query) {
        return containsAny(query,
                "技能书", "书商", "书籍", "力量书", "能力书", "永久能力书",
                "星之书", "价格目录", "风之道", "马之书", "老滑腿", "酱料女皇食谱",
                "鱼饵和浮标", "采矿月刊", "战斗季刊", "樵夫周刊", "星露谷年鉴",
                "怪物图鉴", "洞穴系统地图", "矮人安全手册", "海之宝石", "捕蟹的艺术",
                "伍迪的秘密", "浣熊日志", "神秘之书", "友谊 101", "宝物鉴定指南",
                "Book Of Stars", "Price Catalogue", "Way Of The Wind", "Horse: The Book",
                "Queen Of Sauce Cookbook");
    }

    private boolean looksLikeShopNameQuery(String query) {
        return containsAny(query,
                "皮埃尔", "杂货店", "铁匠", "克林特", "罗宾", "木匠", "玛妮", "威利",
                "鱼店", "科罗布斯", "旅行货车", "货车", "沙漠商人", "书商",
                "冒险家公会", "公会", "矮人商店", "矮人", "绿洲", "桑迪",
                "姜岛商人", "岛屿商人", "齐先生核桃房", "齐钻商店", "核桃房",
                "星之果实餐吧", "星之果实酒吧", "酒吧", "格斯",
                "哈维诊所", "哈维的诊所", "医院", "诊所", "Joja", "joja", "乔家",
                "赌场", "火山矮人", "火山商店", "法师塔", "巫师塔",
                "冰淇淋摊", "冰激凌摊", "帽子店", "帽子鼠", "浣熊商店", "浣熊",
                "大树桩", "复活节", "彩蛋节", "花舞节", "月光水母", "水母节",
                "星露谷展览会", "秋季展览会", "展览会", "万灵节", "沙漠节",
                "Calico Egg Merchant", "卡利科三花蛋", "三花蛋商人");
    }

    private boolean looksLikeCookingFoodQuery(String query) {
        return containsAny(query,
                "蛋糕", "汤", "沙拉", "披萨", "寿司", "生鱼片", "煎蛋", "蛋卷",
                "早餐", "面包", "薄煎饼", "薯饼", "鱼肉卷", "烤鱼", "炸鱿鱼",
                "蘑菇", "火锅", "山药", "玉米饼", "鳟鱼汤", "鲤鱼惊喜",
                "派", "曲奇", "意大利面", "鳗鱼", "布丁", "冰淇淋", "千层酥",
                "红之盛宴", "秋日恩赐", "超级大餐", "蔓越莓酱", "填料",
                "蘸酱", "炒菜", "烤榛子", "脆皮饼", "糖果", "烤面包",
                "炖饭", "烩饭", "松糕", "杂烩汤", "浓汤", "田螺", "蜗牛",
                "虾鸡尾酒", "芒果糯米饭", "芋泥", "咖喱", "墨汁意大利饺",
                "意大利饺", "苔藓汤");
    }

    private boolean containsAny(String query, String... words) {
        for (String word : words) {
            if (query.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private StardewQueryPlan parsePlan(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String json = raw.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(json, StardewQueryPlan.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String chatSafely(List<ChatMessage> messages, ModelTier tier) {
        try {
            return aiChatService.chat(messages, tier);
        } catch (Exception ignored) {
            return null;
        }
    }
}
