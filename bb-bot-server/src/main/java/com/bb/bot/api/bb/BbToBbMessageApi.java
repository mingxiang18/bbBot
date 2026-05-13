package com.bb.bot.api.bb;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.api.AbstractMessageStreamSession;
import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.bb.BbSocketServerMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.Collections;

@Component
public class BbToBbMessageApi {

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        //转换为bb的socket消息结构
        BbSocketServerMessage bbSocketServerMessage = new BbSocketServerMessage();
        BeanUtils.copyProperties(bbSendMessage, bbSocketServerMessage);
        bbSocketServerMessage.setMessageList(bbSendMessage.getMessageList());
        //将bb协议消息封装成socket消息结构
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())) {
                //构建本地图片消息，转换为base64返回
                bbMessageContent.setData((FileUtils.fileToBase64((File) bbMessageContent.getData())));
            }
        }

        //发送消息
        if (bbSendMessage.getWebSocket().isOpen()) {
            bbSendMessage.getWebSocket().send(JSON.toJSONString(bbSocketServerMessage));
        }
    }

    public MessageStreamSession startStream(BbSendMessage bbSendMessage) {
        if (bbSendMessage.getWebSocket() == null || !bbSendMessage.getWebSocket().isOpen()) {
            return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
        }
        return new BbStreamSession(this, bbSendMessage);
    }

    /**
     * BB 私有协议流式呈现：chunked-send（与 OneBot 同策略，未来可在 BbSocketServerMessage
     * 加 isPartial 字段做 edit 风格）。
     */
    private static class BbStreamSession extends AbstractMessageStreamSession {
        private final BbToBbMessageApi api;
        private final BbSendMessage template;

        BbStreamSession(BbToBbMessageApi api, BbSendMessage template) {
            this.api = api;
            this.template = template;
            this.minFlushChars = 60;
            this.minFlushIntervalMs = 1500L;
        }

        @Override
        protected void flush(boolean isFinal) {
            String pending = pendingChunk.toString();
            if (pending.isEmpty()) {
                return;
            }
            String toSend;
            if (isFinal) {
                toSend = pending;
                pendingChunk.setLength(0);
            } else {
                int cut = findSentenceBoundary(pending);
                if (cut <= 0) {
                    return;
                }
                toSend = pending.substring(0, cut);
                pendingChunk.delete(0, cut);
            }
            BbSendMessage envelope = new BbSendMessage();
            envelope.setBotType(template.getBotType());
            envelope.setMessageType(template.getMessageType());
            envelope.setUserId(template.getUserId());
            envelope.setGroupId(template.getGroupId());
            envelope.setReceiveMessageId(template.getReceiveMessageId());
            envelope.setMessageSeq(template.getMessageSeq());
            envelope.setWebSocket(template.getWebSocket());
            envelope.setConfig(template.getConfig());
            envelope.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(toSend)));
            api.sendMessage(envelope);
        }

        private int findSentenceBoundary(String s) {
            for (int i = s.length() - 1; i >= 0; i--) {
                char c = s.charAt(i);
                if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                    return i + 1;
                }
            }
            return -1;
        }
    }

}
