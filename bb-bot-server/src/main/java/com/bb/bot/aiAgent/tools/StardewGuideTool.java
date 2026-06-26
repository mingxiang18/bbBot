package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.handler.stardew.StardewGuideAssistantService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StardewGuideTool {

    private final StardewGuideAssistantService guideAssistantService;

    public StardewGuideTool(StardewGuideAssistantService guideAssistantService) {
        this.guideAssistantService = guideAssistantService;
    }

    @AiTool(
            name = "stardew_guide",
            description = "查询星露谷物语攻略知识库。用户问夏天能钓什么鱼、某个收集包需要什么、"
                    + "居民在某季节日期时间的位置、居民生日/送礼偏好、工具升级材料金钱/具体升级档位/鱼竿购买解锁、"
                    + "蟹笼/传说鱼/大家族任务鱼类/钓鱼果冻、作物成熟天数/收益/收集包用途、"
                    + "果树种植规则/树苗价格/温室果树/姜岛果树、苹果/石榴/香蕉/芒果等水果来源、"
                    + "资源/物品怎么获取、怪物掉落/怪物战利品/煤尘精灵刷煤/太阳精华/虚空精华/蝙蝠翅膀/虫肉/史莱姆泥/骨头碎片哪里刷、"
                    + "建筑建造/升级材料金钱/动物解锁/房屋升级、方尖塔/黄金钟/祝尼魔小屋/社区升级/城镇捷径、"
                    + "动物养殖、动物心情好感、鸡蛋/牛奶/羊奶/羊毛/虚空蛋/鸵鸟蛋等动物产品来源、"
                    + "博物馆捐赠、古物/文物、矿物、晶球、万象晶球、古物宝藏、缺失藏品怎么补、"
                    + "商店/商人营业时间、背包升级、常用物品在哪里买、沙漠商人/书商/旅行货车/Joja/赌场/法师塔/火山矮人/浣熊商店/节日商店/沙漠节商店、"
                    + "机器/加工设备/小桶/罐头瓶/鱼熏机/脱水机/诱饵制造机的配方材料和加工建议、"
                    + "洒水器/炸弹/楼梯/箱子/标牌/稻草人/太阳能板/重型熔炉/史莱姆设备/骨头磨坊/晶球破开器等制作设备材料、"
                    + "肥料/生长激素/保湿土壤/树肥/传送图腾/雨水图腾/怪物香水/仙尘/戒指/铱环等制作材料和使用建议、"
                    + "鱼饵/魔法鱼饵/挑战鱼饵/蟹笼/钓具/浮标/寻宝器/旋式鱼饵等钓鱼装备制作材料和使用建议、"
                    + "料理配方材料、食物/饮料 buff、buff 叠加/覆盖规则、骷髅洞穴/钓鱼/战斗/耕种推荐吃什么、"
                    + "技能等级快速升级、战斗等级低怎么练、技能职业选择、技能书/战斗季刊/星之书、"
                    + "价格目录/风之道/马之书/酱料女皇食谱/力量书有什么用或在哪里买、常见机制有什么推荐方式时调用。"
                    + "工具内部会做问题分类、关键词检索和自然语言整合；优先用用户原问题调用一次，"
                    + "不要告诉用户数据版本、日期、来源链接或 Wiki 字样；"
                    + "不读取存档、不做随机预测。"
    )
    public Map<String, Object> guide(
            @AiToolParam(name = "query", description = "用户的星露谷攻略问题，保留季节、日期、时间、天气等条件")
            String query
    ) {
        String answer = guideAssistantService.answer(query);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("intent", "ai_synthesized");
        out.put("answer", answer);
        out.put("evidence", answer);
        out.put("replyInstruction", "请直接基于 answer 回复用户；不要提数据版本、校验日期、来源链接或 Wiki；不要声称读取了用户存档。");
        return out;
    }
}
