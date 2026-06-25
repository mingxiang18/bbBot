package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.handler.stardew.StardewGuideResult;
import com.bb.bot.handler.stardew.StardewGuideService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StardewGuideTool {

    private final StardewGuideService guideService;

    public StardewGuideTool(StardewGuideService guideService) {
        this.guideService = guideService;
    }

    @AiTool(
            name = "stardew_guide",
            description = "查询星露谷物语攻略知识库。用户问夏天能钓什么鱼、某个收集包需要什么、"
                    + "居民在某季节日期时间的位置、居民生日/送礼偏好、工具升级材料金钱/具体升级档位/鱼竿购买解锁、"
                    + "蟹笼/传说鱼/大家族任务鱼类/钓鱼果冻、作物成熟天数/收益/收集包用途、"
                    + "资源/物品怎么获取、建筑建造/升级材料金钱/动物解锁/房屋升级、"
                    + "博物馆捐赠、古物/文物、矿物、晶球、万象晶球、古物宝藏、缺失藏品怎么补、"
                    + "商店/商人营业时间、背包升级、常用物品在哪里买、沙漠商人/书商/旅行货车、"
                    + "机器/加工设备/小桶/罐头瓶/鱼熏机/脱水机/诱饵制造机的配方材料和加工建议、"
                    + "料理配方材料、食物/饮料 buff、骷髅洞穴/钓鱼/战斗/耕种推荐吃什么、"
                    + "技能/常见机制有什么推荐方式时调用。"
                    + "这是检索证据工具，不是最终回复工具；可用用户原问题和你改写出的关键词多次查询，"
                    + "再把有效证据整合成自然语言回复。不要告诉用户数据版本、日期、来源链接或 Wiki 字样；"
                    + "不读取存档、不做随机预测。"
    )
    public Map<String, Object> guide(
            @AiToolParam(name = "query", description = "用户的星露谷攻略问题，保留季节、日期、时间、天气等条件")
            String query
    ) {
        StardewGuideResult result = guideService.answer(query);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("intent", result.getIntent());
        out.put("evidence", result.getAnswer());
        out.put("replyInstruction", "请把 evidence 整合成自然、简洁、可执行的中文回复；不要提数据版本、校验日期、来源链接或 Wiki；不要声称读取了用户存档。");
        return out;
    }
}
