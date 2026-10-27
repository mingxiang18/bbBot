package com.bb.bot.api.oneBot;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.config.OnebotConfig;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将 BB 出站消息转换为 OneBot 消息段，并等待网关返回实际发送结果。 */
@Component
public class OneBotMessageApi {

    /** 群聊和私聊使用各自的 API，成功后保留网关消息 ID，供引用与记忆记录使用。 */
    public void sendMessage(BbSendMessage message) {
        if (message == null
                || message.getMessageList() == null
                || message.getMessageList().isEmpty()) {
            return;
        }

        List<Map<String, Object>> segments = buildSegments(message);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", segments);

        String action;
        if (MessageType.GROUP.equals(message.getMessageType())) {
            action = "send_group_msg";
            params.put("group_id", target(message.getGroupId()));
        } else if (MessageType.PRIVATE.equals(message.getMessageType())) {
            action = "send_private_msg";
            params.put("user_id", target(message.getUserId()));
        } else {
            throw new IllegalArgumentException("Unsupported OneBot message type");
        }
        if (!(message.getConfig() instanceof OnebotConfig config)) {
            throw new IllegalStateException("OneBot configuration missing from outbound message");
        }

        JSONObject response =
                config.getSession()
                        .call(action, params, message.getWebSocket(), config.getApiTimeoutMs());
        JSONObject data = response.getJSONObject("data");
        if (data != null) {
            message.setSentMessageId(data.getString("message_id"));
        }
    }

    /** 引用段放在最前面，最多保留一个；不支持的内容显式报错，避免静默丢失附件。 */
    private List<Map<String, Object>> buildSegments(BbSendMessage message) {
        List<Map<String, Object>> segments = new ArrayList<>();
        List<Map<String, Object>> replies = new ArrayList<>();
        for (BbMessageContent content : message.getMessageList()) {
            if (content == null || content.getType() == null || content.getData() == null) {
                continue;
            }

            Object value = content.getData();
            switch (content.getType()) {
                case BbSendMessageType.TEXT -> {
                    if (!value.toString().isEmpty()) {
                        segments.add(segment("text", "text", value.toString()));
                    }
                }
                case BbSendMessageType.AT -> {
                    if (MessageType.GROUP.equals(message.getMessageType())) {
                        segments.add(segment("at", "qq", value.toString()));
                        segments.add(segment("text", "text", " "));
                    }
                }
                case BbSendMessageType.LOCAL_IMAGE -> {
                    if (!(value instanceof File file) || !file.isFile()) {
                        throw new IllegalArgumentException("OneBot image file does not exist");
                    }
                    // 网关可能不在同一台机器，不能把 bbBot 的本地路径直接发过去。
                    String imageData = "base64://" + FileUtils.fileToBase64(file);
                    segments.add(segment("image", "file", imageData));
                }
                case BbSendMessageType.NET_IMAGE ->
                        segments.add(segment("image", "file", value.toString()));
                case BbSendMessageType.REPLY -> {
                    if (replies.isEmpty()) {
                        replies.add(segment("reply", "id", value.toString()));
                    }
                }
                default ->
                        throw new UnsupportedOperationException(
                                "Unsupported OneBot outbound content: " + content.getType());
            }
        }

        if (segments.isEmpty()) {
            throw new IllegalArgumentException("OneBot message has no sendable content");
        }
        segments.addAll(0, replies);
        return segments;
    }

    private static Map<String, Object> segment(String type, String key, String value) {
        return Map.of("type", type, "data", Map.of(key, value));
    }

    /** OneBot 目标账号/群号使用正整数，与允许负数的消息 ID 分开校验。 */
    private static long target(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // 统一在下方返回参数错误，调用方不应把错误目标当成发送成功。
        }
        throw new IllegalArgumentException("Invalid OneBot target ID");
    }

    /** OneBot 没有通用的消息编辑 API，流式内容收集完成后只发送一次。 */
    public MessageStreamSession startStream(BbSendMessage message) {
        return new FallbackMessageStreamSession(message, this::sendMessage);
    }
}
