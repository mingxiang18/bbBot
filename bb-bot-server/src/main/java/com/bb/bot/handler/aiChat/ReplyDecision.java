package com.bb.bot.handler.aiChat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * AI 自动回复决策结果。
 *
 * @author ren
 */
@Getter
@RequiredArgsConstructor
public class ReplyDecision {

    /** 是否需要回复。 */
    private final boolean shouldReply;

    /** 触发的线索列表（用于在 personality 后追加记忆）。 */
    private final List<String> clues;

    /**
     * 是否为「直接对话」触发：私聊 / 群里 @ 机器人。
     *
     * <p>区别于群聊概率自动回复——只有直接对话才会给模型挂载工具，让它自行决定
     * 是聊天还是干活；概率自动回复保持纯聊天，避免误触发工具和额外开销。</p>
     */
    private final boolean directTrigger;

    public static ReplyDecision skip() {
        return new ReplyDecision(false, Collections.emptyList(), false);
    }

    /** 概率自动回复触发（非直接对话）。 */
    public static ReplyDecision reply(List<String> clues) {
        return new ReplyDecision(true, clues == null ? Collections.emptyList() : clues, false);
    }

    /** 直接对话触发（私聊 / @机器人）。 */
    public static ReplyDecision replyDirect(List<String> clues) {
        return new ReplyDecision(true, clues == null ? Collections.emptyList() : clues, true);
    }
}
