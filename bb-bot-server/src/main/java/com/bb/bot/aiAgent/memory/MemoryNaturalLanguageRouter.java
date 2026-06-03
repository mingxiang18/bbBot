package com.bb.bot.aiAgent.memory;

import com.bb.bot.common.util.BbReplies;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言记忆意图路由（Phase 4）。普通聊天前置：识别"记住/忘掉/你记得我什么/别记/记错了"，
 * 命中则直接处理并回复、不进入常规聊天与 LLM。普通用户也能用（只触及本人记忆）。
 *
 * <p>意图前缀都锚定在句首且较显式，避免劫持普通闲聊。</p>
 */
@Slf4j
@Component
public class MemoryNaturalLanguageRouter {

    @Autowired
    private MemoryCommandService commandService;

    @Autowired
    private BbReplies replies;

    // 句首显式前缀，避免误伤"我记住了你说的"这类陈述
    private static final Pattern REMEMBER = Pattern.compile("^\\s*(?:请\\s*)?(?:记住|记一下|记下来?|帮我记住?)[:：,，\\s]+(.{1,500}?)\\s*$");
    private static final Pattern FORGET = Pattern.compile("^\\s*(?:忘掉|忘记|删掉记忆|别记得)[:：,，\\s]*(.{1,200}?)\\s*$");
    private static final Pattern DONT_RECORD = Pattern.compile("^\\s*(?:这条?别记|别记这条?|这个别记|这句别记|这段别记)\\s*$");
    private static final Pattern WHAT_REMEMBER = Pattern.compile(".*你(?:还|都)?记得(?:我|关于我)(?:什么|啥|哪些|的什么)?.*|.*我在你(?:印象|记忆)里.*");
    private static final Pattern WRONG = Pattern.compile("^\\s*(?:这条?记错了|你?记错了|记错了吧?)\\s*$");

    /** 返回 true 表示已作为记忆意图处理（调用方应 return，不再走常规聊天）。 */
    public boolean tryHandle(BbReceiveMessage msg, String text) {
        if (StringUtils.isBlank(text)) return false;
        String userId = msg.getUserId();

        Matcher rem = REMEMBER.matcher(text);
        if (rem.matches()) {
            reply(msg, commandService.writeExplicit(userId, rem.group(1)));
            return true;
        }
        Matcher forget = FORGET.matcher(text);
        if (forget.matches()) {
            int n = commandService.forget(userId, forget.group(1));
            reply(msg, n > 0 ? ("好的，已经忘掉 " + n + " 条相关记忆。") : "我没找到相关的记忆呢，可以先问「你记得我什么」看看。");
            return true;
        }
        if (DONT_RECORD.matcher(text).matches()) {
            reply(msg, "好的，这条我就不记进长期记忆啦。");
            return true;
        }
        if (WRONG.matcher(text).matches()) {
            reply(msg, "抱歉记错了～你可以说「忘掉……」把记错的那条删掉，或者直接告诉我正确的，我重新记。");
            return true;
        }
        if (WHAT_REMEMBER.matcher(text).matches()) {
            reply(msg, commandService.readableSelfMemory(userId));
            return true;
        }
        return false;
    }

    private void reply(BbReceiveMessage msg, String text) {
        if (StringUtils.isNotBlank(msg.getGroupId())) {
            replies.atText(msg, text);
        } else {
            replies.text(msg, text);
        }
    }
}
