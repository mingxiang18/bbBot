package com.bb.bot.api.oneBot;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.api.AbstractMessageStreamSession;
import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.oneBot.Action;
import com.bb.bot.entity.oneBot.Message;
import com.bb.bot.entity.oneBot.MessageContent;
import com.bb.bot.common.util.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class OneBotMessageApi {

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }
        //将bb协议消息封装成oneBot结构消息
        List<MessageContent> onebotMessageContentList = new ArrayList<>();
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.TEXT.equals(bbMessageContent.getType())) {
                //构建文本消息
                onebotMessageContentList.add(MessageContent.buildTextContent(bbMessageContent.getData().toString()));
            }else if (BbSendMessageType.AT.equals(bbMessageContent.getType()) && MessageType.GROUP.equals(bbSendMessage.getMessageType())) {
                //构建at消息
                onebotMessageContentList.add(MessageContent.buildAtMessageContent(bbMessageContent.getData().toString()));
                //at之后要加空格
                onebotMessageContentList.add(MessageContent.buildTextContent(" "));
            }else if (BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())) {
                //构建本地图片消息
                onebotMessageContentList.add(MessageContent.buildImageMessageContentFromBase64(FileUtils.fileToBase64((File) bbMessageContent.getData())));
            }else if (BbSendMessageType.NET_IMAGE.equals(bbMessageContent.getType())) {
                //构建网络图片消息
                onebotMessageContentList.add(MessageContent.buildImageMessageContentFromPath(bbMessageContent.getData().toString()));
            }
        }

        Action action = null;
        //根据消息类型封装群组或个人消息
        if (MessageType.GROUP.equals(bbSendMessage.getMessageType())) {
            action = Action.buildPrivateMessageSendAction(Message.builder()
                    .groupId(bbSendMessage.getGroupId())
                    .message(onebotMessageContentList)
                    .build());
        }else if (MessageType.PRIVATE.equals(bbSendMessage.getMessageType())) {
            action = Action.buildPrivateMessageSendAction(Message.builder()
                    .userId(bbSendMessage.getUserId())
                    .message(onebotMessageContentList)
                    .build());
        }

        //发送消息
        if (action != null && bbSendMessage.getWebSocket().isOpen()) {
            bbSendMessage.getWebSocket().send(JSON.toJSONString(action));
        }
    }

    public MessageStreamSession startStream(BbSendMessage bbSendMessage) {
        if (bbSendMessage.getWebSocket() == null || !bbSendMessage.getWebSocket().isOpen()) {
            return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
        }
        return new OneBotStreamSession(this, bbSendMessage);
    }

    /**
     * OneBot 流式呈现：协议不支持 edit-message，走 chunked-send。
     * 累计达到 80 字符且遇到句子边界 / 换行时切段；最终 flush 把剩余发完。
     */
    private static class OneBotStreamSession extends AbstractMessageStreamSession {
        private final OneBotMessageApi api;
        private final BbSendMessage template;
        private int chunkIndex = 0;

        OneBotStreamSession(OneBotMessageApi api, BbSendMessage template) {
            this.api = api;
            this.template = template;
            this.minFlushChars = 80;
            this.minFlushIntervalMs = 2500L;
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
                    // 没找到句子边界，等下一轮累计
                    return;
                }
                toSend = pending.substring(0, cut);
                pendingChunk.delete(0, cut);
            }
            chunkIndex++;
            BbSendMessage envelope = cloneEnvelope(template);
            envelope.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(toSend)));
            api.sendMessage(envelope);
        }

        /**
         * 在字符串中找句子边界（句号 / 问号 / 感叹号 / 换行），返回切分点（包含边界字符）。
         * 找不到返回 -1。
         */
        private int findSentenceBoundary(String s) {
            for (int i = s.length() - 1; i >= 0; i--) {
                char c = s.charAt(i);
                if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                    return i + 1;
                }
            }
            return -1;
        }

        private BbSendMessage cloneEnvelope(BbSendMessage src) {
            BbSendMessage dst = new BbSendMessage();
            dst.setBotType(src.getBotType());
            dst.setMessageType(src.getMessageType());
            dst.setUserId(src.getUserId());
            dst.setGroupId(src.getGroupId());
            dst.setReceiveMessageId(src.getReceiveMessageId());
            dst.setMessageSeq(src.getMessageSeq() + chunkIndex);
            dst.setWebSocket(src.getWebSocket());
            dst.setConfig(src.getConfig());
            return dst;
        }
    }

}
