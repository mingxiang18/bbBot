package com.bb.bot.api.oneBot;

import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.config.OnebotConfig;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.common.util.FileUtils;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OneBotMessageApi {
    public void sendMessage(BbSendMessage message) {
        if (message == null || message.getMessageList() == null || message.getMessageList().isEmpty()) return;
        List<Map<String, Object>> segments = new ArrayList<>();
        List<Map<String, Object>> replies = new ArrayList<>();
        for (BbMessageContent content : message.getMessageList()) {
            if (content == null || content.getType() == null || content.getData() == null) continue;
            Object value = content.getData();
            switch (content.getType()) {
                case BbSendMessageType.TEXT -> {
                    if (!value.toString().isEmpty()) segments.add(segment("text", "text", value.toString()));
                }
                case BbSendMessageType.AT -> {
                    if (MessageType.GROUP.equals(message.getMessageType())) {
                        segments.add(segment("at", "qq", value.toString()));
                        segments.add(segment("text", "text", " "));
                    }
                }
                case BbSendMessageType.LOCAL_IMAGE -> {
                    if (!(value instanceof File file) || !file.isFile()) throw new IllegalArgumentException("OneBot image file does not exist");
                    segments.add(segment("image", "file", "base64://" + FileUtils.fileToBase64(file)));
                }
                case BbSendMessageType.NET_IMAGE -> segments.add(segment("image", "file", value.toString()));
                case BbSendMessageType.REPLY -> {
                    if (replies.isEmpty()) replies.add(segment("reply", "id", value.toString()));
                }
                default -> throw new UnsupportedOperationException("Unsupported OneBot outbound content: " + content.getType());
            }
        }
        if (segments.isEmpty()) throw new IllegalArgumentException("OneBot message has no sendable content");
        segments.addAll(0, replies);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", segments);
        String action;
        if (MessageType.GROUP.equals(message.getMessageType())) {
            action = "send_group_msg"; params.put("group_id", target(message.getGroupId()));
        } else if (MessageType.PRIVATE.equals(message.getMessageType())) {
            action = "send_private_msg"; params.put("user_id", target(message.getUserId()));
        } else throw new IllegalArgumentException("Unsupported OneBot message type");
        if (!(message.getConfig() instanceof OnebotConfig config))
            throw new IllegalStateException("OneBot configuration missing from outbound message");
        JSONObject response = config.getSession().call(action, params, message.getWebSocket(), config.getApiTimeoutMs());
        JSONObject data = response.getJSONObject("data");
        if (data != null) message.setSentMessageId(data.getString("message_id"));
    }
    private static Map<String, Object> segment(String type, String key, String value) {
        return Map.of("type", type, "data", Map.of(key, value));
    }
    private static long target(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Invalid OneBot target ID");
    }
    /** OneBot has no edit action; accumulate deltas and send once at completion. */
    public MessageStreamSession startStream(BbSendMessage message) {
        return new FallbackMessageStreamSession(message, this::sendMessage);
    }
}
