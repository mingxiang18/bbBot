package com.bb.bot.handler.aiChat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    /**
     * 是否为「直接对话」触发：私聊 / 群里 @ 机器人。
     *
     * <p>区别于群聊概率自动回复——只有直接对话才会给模型挂载工具，让它自行决定
     * 是聊天还是干活；概率自动回复保持纯聊天，避免误触发工具和额外开销。</p>
     */
    private final boolean directTrigger;

    public static ReplyDecision skip() {
        return new ReplyDecision(false, false);
    }

    /** 概率自动回复触发（非直接对话）。 */
    public static ReplyDecision reply() {
        return new ReplyDecision(true, false);
    }

    /** 直接对话触发（私聊 / @机器人）。 */
    public static ReplyDecision replyDirect() {
        return new ReplyDecision(true, true);
    }
}
