package com.bb.bot.common.util;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        sendInternal(out, "atText", text == null ? 0 : text.length());
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
        sendInternal(out, "text", text == null ? 0 : text.length());
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
        sendInternal(out, "send", contents == null ? 0 : contents.size());
    }

    /**
     * 统一发送出口：记录发送尝试与结果，发送失败的异常照原样上抛（不改变现有调用方行为）。
     *
     * @param out   待发送消息
     * @param via   发送入口名（atText / text / send）
     * @param size  载荷规模（文本长度或内容条数），便于排查空回复
     */
    private void sendInternal(BbSendMessage out, String via, int size) {
        long start = System.currentTimeMillis();
        try {
            messageApi.sendMessage(out);
            log.info("回复已发出 via={} platform={} type={} group={} user={} replyTo={} size={} cost={}ms",
                    via, out.getBotType(), out.getMessageType(), out.getGroupId(), out.getUserId(),
                    out.getReceiveMessageId(), size, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            log.error("回复发送失败 via={} platform={} type={} group={} user={} replyTo={} cost={}ms",
                    via, out.getBotType(), out.getMessageType(), out.getGroupId(), out.getUserId(),
                    out.getReceiveMessageId(), System.currentTimeMillis() - start, e);
            throw e;
        }
    }
}
