package com.bb.bot.handler.stardew;

import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@BootEventHandler(botType = BotType.BB, name = "星露谷攻略")
public class BbStardewHandler {

    private final StardewGuideService guideService;
    private final BbReplies replies;

    public BbStardewHandler(StardewGuideService guideService, BbReplies replies) {
        this.guideService = guideService;
        this.replies = replies;
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?星露谷", "^/?stardew"}, name = "星露谷攻略查询")
    public void guide(BbReceiveMessage message) {
        try {
            StardewGuideResult result = guideService.answer(message.getMessage());
            replies.atText(message, render(result));
        } catch (Exception e) {
            log.warn("星露谷攻略查询失败 message={}", message.getMessage(), e);
            replies.atText(message, "星露谷攻略查询失败：" + e.getMessage());
        }
    }

    private String render(StardewGuideResult result) {
        return result.getAnswer();
    }
}
