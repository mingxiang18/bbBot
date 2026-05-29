package com.bb.bot.common.util;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 统一消息回复入口工具 bean。
 *
 * <p>收敛各 handler / API 中重复的「@用户 + 文本」「纯文本」「自定义内容列表」回复构造逻辑，
 * 统一以接收消息为来源构造 {@link BbSendMessage} 并通过 {@link BbMessageApi} 发送。</p>
 *
 * @author ren
 */
@Component
public class BbReplies {

    private final BbMessageApi messageApi;

    @Autowired
    public BbReplies(BbMessageApi messageApi) {
        this.messageApi = messageApi;
    }

    /**
     * @用户 + 文本：先 at 来源消息的发送者，再附文本。
     *
     * @param src  来源接收消息
     * @param text 回复文本（透传，不做改写）
     */
    public void atText(BbReceiveMessage src, String text) {
        BbSendMessage out = new BbSendMessage(src);
        out.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(src.getUserId()),
                BbMessageContent.buildTextContent(text)));
        messageApi.sendMessage(out);
    }

    /**
     * 纯文本回复（无 @）。
     *
     * @param src  来源接收消息
     * @param text 回复文本（透传，不做改写）
     */
    public void text(BbReceiveMessage src, String text) {
        BbSendMessage out = new BbSendMessage(src);
        out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        messageApi.sendMessage(out);
    }

    /**
     * 自定义内容列表回复：调用方自行组织消息内容，原样透传。
     *
     * @param src      来源接收消息
     * @param contents 消息内容列表
     */
    public void send(BbReceiveMessage src, List<BbMessageContent> contents) {
        BbSendMessage out = new BbSendMessage(src);
        out.setMessageList(contents);
        messageApi.sendMessage(out);
    }
}
