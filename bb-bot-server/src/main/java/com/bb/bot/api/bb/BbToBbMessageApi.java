package com.bb.bot.api.bb;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.api.AbstractMessageStreamSession;
import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.connection.bb.BbWebSocketServer;
import com.bb.bot.constant.BbCapability;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.BbStreamState;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.bb.BbSocketServerMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

@Component
public class BbToBbMessageApi {

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        //转换为bb的socket消息结构（streamId / streamState / messageSeq 由 BeanUtils 一并拷贝）
        BbSocketServerMessage bbSocketServerMessage = new BbSocketServerMessage();
        BeanUtils.copyProperties(bbSendMessage, bbSocketServerMessage);
        bbSocketServerMessage.setMessageList(bbSendMessage.getMessageList());
        //将bb协议消息封装成socket消息结构：本地图片 / 本地文件转 base64
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())
                    || BbSendMessageType.LOCAL_FILE.equals(bbMessageContent.getType())) {
                if (bbMessageContent.getData() instanceof File) {
                    File file = (File) bbMessageContent.getData();
                    if (bbMessageContent.getFileName() == null) {
                        bbMessageContent.setFileName(file.getName());
                    }
                    bbMessageContent.setData(FileUtils.fileToBase64(file));
                }
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
        //客户端上报 stream 能力 → 走 streamId 帧式真流式；否则回退分段连发
        boolean trueStreaming = BbWebSocketServer.getCapabilities(bbSendMessage.getWebSocket())
                .contains(BbCapability.STREAM);
        return new BbStreamSession(this, bbSendMessage, trueStreaming);
    }

    /**
     * BB 私有协议流式呈现。
     *
     * <ul>
     *   <li>客户端支持 {@code stream} 能力：发 streamId 串起的 start/delta/end 帧，
     *       客户端 edit-in-place 续写同一条消息；短回复（单帧成型）退化为普通消息。</li>
     *   <li>不支持：回退老的分段连发（按句末切段，每段一条独立消息）。</li>
     * </ul>
     */
    private static class BbStreamSession extends AbstractMessageStreamSession {
        private final BbToBbMessageApi api;
        private final BbSendMessage template;
        private final boolean trueStreaming;
        private final String streamId;
        private int framesSent = 0;

        BbStreamSession(BbToBbMessageApi api, BbSendMessage template, boolean trueStreaming) {
            this.api = api;
            this.template = template;
            this.trueStreaming = trueStreaming;
            this.streamId = "stream-" + UUID.randomUUID();
            if (trueStreaming) {
                // 客户端原地续写，可以更激进地 flush，吐字更跟手
                this.minFlushChars = 24;
                this.minFlushIntervalMs = 700L;
            } else {
                this.minFlushChars = 60;
                this.minFlushIntervalMs = 1500L;
            }
        }

        @Override
        protected void flush(boolean isFinal) {
            if (trueStreaming) {
                flushStreaming(isFinal);
            } else {
                flushChunked(isFinal);
            }
        }

        /** 真流式：start/delta/end 帧，客户端按 streamId 续写同一条消息。 */
        private void flushStreaming(boolean isFinal) {
            String pending = pendingChunk.toString();
            // 整条回复一次成型 → 不必走流式帧，直接发普通消息
            if (framesSent == 0 && isFinal) {
                pendingChunk.setLength(0);
                if (!pending.isEmpty()) {
                    sendFrame(pending, null);
                }
                return;
            }
            // 中途 flush 但还没有内容：跳过
            if (!isFinal && pending.isEmpty()) {
                return;
            }
            pendingChunk.setLength(0);
            String state = framesSent == 0 ? BbStreamState.START
                    : (isFinal ? BbStreamState.END : BbStreamState.DELTA);
            sendFrame(pending, state);
            framesSent++;
        }

        /** 回退：按句末边界切段，每段一条独立的普通消息（老客户端行为）。 */
        private void flushChunked(boolean isFinal) {
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
            sendFrame(toSend, null);
        }

        private void sendFrame(String text, String streamState) {
            BbSendMessage envelope = new BbSendMessage();
            envelope.setBotType(template.getBotType());
            envelope.setMessageType(template.getMessageType());
            envelope.setUserId(template.getUserId());
            envelope.setGroupId(template.getGroupId());
            envelope.setReceiveMessageId(template.getReceiveMessageId());
            envelope.setMessageSeq(template.getMessageSeq());
            envelope.setWebSocket(template.getWebSocket());
            envelope.setConfig(template.getConfig());
            envelope.setStreamId(streamState == null ? null : streamId);
            envelope.setStreamState(streamState);
            envelope.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
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
