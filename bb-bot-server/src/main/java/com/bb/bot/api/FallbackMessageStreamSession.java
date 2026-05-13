package com.bb.bot.api;

import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 平台未实现流式呈现时的兜底实现：把所有 delta 缓冲到 complete()，一次性 sendMessage。
 *
 * <p>用法：构造时传入「能拿到完整文本就把它发出去」的回调，通常是
 * {@code text -> messageApi.sendMessage(envelopeWithText(text))}。</p>
 */
@Slf4j
public class FallbackMessageStreamSession extends AbstractMessageStreamSession {

    private final BbSendMessage envelope;
    private final Consumer<BbSendMessage> sender;

    public FallbackMessageStreamSession(BbSendMessage envelope, Consumer<BbSendMessage> sender) {
        this.envelope = envelope;
        this.sender = sender;
        // fallback 模式下不需要中途 flush，直接 complete 再发
        this.minFlushChars = Integer.MAX_VALUE;
        this.minFlushIntervalMs = Long.MAX_VALUE;
    }

    @Override
    protected void flush(boolean isFinal) {
        if (!isFinal) {
            return;
        }
        String text = buffer.toString();
        if (text.isEmpty()) {
            return;
        }
        List<BbMessageContent> contents = new ArrayList<>();
        contents.add(BbMessageContent.buildTextContent(text));
        envelope.setMessageList(contents);
        sender.accept(envelope);
    }
}
