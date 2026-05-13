package com.bb.bot.handler.aiChat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * AI 自动回复决策结果：是否回复 + 触发的线索列表（用于在 personality 后追加记忆）。
 *
 * @author ren
 */
@Getter
@RequiredArgsConstructor
public class ReplyDecision {

    private final boolean shouldReply;
    private final List<String> clues;

    public static ReplyDecision skip() {
        return new ReplyDecision(false, Collections.emptyList());
    }

    public static ReplyDecision reply(List<String> clues) {
        return new ReplyDecision(true, clues == null ? Collections.emptyList() : clues);
    }
}
