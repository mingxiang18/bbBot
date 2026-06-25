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
                    + "商店/商人营业时间、背包升级、常用物品在哪里买、沙漠商人/书商/旅行货车、"
                    + "机器/加工设备/小桶/罐头瓶/鱼熏机/脱水机/诱饵制造机的配方材料和加工建议、"
                    + "技能/常见机制有什么推荐方式时调用。"
                    + "不读取存档、不做随机预测；如果本地库未建模，会返回官方 Wiki 兜底摘要和来源。"
    )
    public Map<String, Object> guide(
            @AiToolParam(name = "query", description = "用户的星露谷攻略问题，保留季节、日期、时间、天气等条件")
            String query
    ) {
        StardewGuideResult result = guideService.answer(query);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intent", result.getIntent());
        out.put("answer", result.getAnswer());
        out.put("sourceUrls", result.getSourceUrls());
        out.put("gameVersion", result.getGameVersion());
        out.put("lastCheckedAt", result.getLastCheckedAt());
        out.put("note", "请基于 answer 简短回复；不要扩写没有来源的数据；不要声称读取了用户存档。");
        return out;
    }
}
