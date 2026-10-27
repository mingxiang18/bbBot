package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.config.OnebotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.WebSocket;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class OneBotMessageHandler {
    private static final Pattern CQ = Pattern.compile("\\[CQ:([a-zA-Z0-9_]+)((?:,[^\\]]*)?)\\]");
    private OneBotMessageHandler() { }

    public static void handleMessage(String name, OnebotConfig config, WebSocket socket,
                                     String raw, ApplicationEventPublisher publisher) {
        try {
            JSONObject event = JSON.parseObject(raw);
            if (event == null || config.getSession().response(socket, event)) return;
            if (config.getSession().connection() != socket) return;
            BbReceiveMessage message = parseMessage(config, socket, event);
            if (message != null) config.getSession().dispatch(() -> publisher.publishEvent(message));
        } catch (RuntimeException e) {
            // Never log raw payloads: they can contain private text or media data.
            log.warn("OneBot {} rejected inbound frame: {}", name, e.getClass().getSimpleName());
        }
    }

    public static BbReceiveMessage parseMessage(OnebotConfig config, WebSocket socket, JSONObject event) {
        if (!"message".equals(event.getString("post_type"))) return null;
        String type = event.getString("message_type");
        if (!MessageType.GROUP.equals(type) && !MessageType.PRIVATE.equals(type)) return null;
        String self = event.getString("self_id");
        if (StringUtils.isNotBlank(config.getQq()) && StringUtils.isNotBlank(self)
                && !config.getQq().equals(self)) return null;
        if (StringUtils.isBlank(config.getQq()) && StringUtils.isNotBlank(self)) config.setQq(self);
        String user = event.getString("user_id");
        if (StringUtils.isBlank(user) || user.equals(StringUtils.defaultIfBlank(self, config.getQq()))) return null;
        String group = event.getString("group_id");
        if (MessageType.GROUP.equals(type) && StringUtils.isBlank(group)) return null;
        JSONObject sender = event.getJSONObject("sender");
        String nickname = sender == null ? user : StringUtils.firstNonBlank(
                sender.getString("card"), sender.getString("nickname"), user);
        BbReceiveMessage result = new BbReceiveMessage();
        result.setBotType(BotType.ONEBOT);
        result.setConfig(config);
        result.setWebSocket(socket);
        result.setMessageType(type);
        result.setUserId(user);
        result.setSender(new MessageUser(user, nickname));
        result.setGroupId(MessageType.GROUP.equals(type) ? group : null);
        // Keep the gateway ID usable for reply/get_msg; deduplication is scoped separately.
        result.setMessageId(StringUtils.defaultIfBlank(event.getString("message_id"), UUID.randomUUID().toString()));
        Long time = event.getLong("time");
        if (time != null && time > 0) result.setSendTime(Instant.ofEpochSecond(time)
                .atZone(ZoneId.systemDefault()).toLocalDateTime());
        List<BbMessageContent> content = new ArrayList<>();
        List<MessageUser> mentions = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Object item : segments(event.get("message"))) {
            if (!(item instanceof JSONObject segment)) continue;
            JSONObject data = segment.getJSONObject("data");
            if (data == null) continue;
            String value;
            switch (StringUtils.defaultString(segment.getString("type"))) {
                case "text" -> {
                    value = data.getString("text");
                    if (value != null) { text.append(value); content.add(BbMessageContent.buildTextContent(value)); }
                }
                case "at" -> {
                    value = data.getString("qq");
                    if (StringUtils.isNotBlank(value)) {
                        mentions.add(new MessageUser(value, value.equals(config.getQq())));
                        content.add(BbMessageContent.buildAtMessageContent(value));
                    }
                }
                case "image" -> {
                    value = StringUtils.firstNonBlank(data.getString("url"), data.getString("file"));
                    if (isHttp(value)) content.add(BbMessageContent.buildNetImageMessageContent(value));
                }
                case "reply" -> {
                    value = data.getString("id");
                    if (StringUtils.isNotBlank(value)) content.add(BbMessageContent.buildReplyMessageContent(value));
                }
                case "file" -> {
                    value = data.getString("url");
                    if (isHttp(value)) content.add(BbMessageContent.buildNetFileMessageContent(value, data.getString("name")));
                }
                default -> { /* Keep supported segments alongside unsupported ones. */ }
            }
        }
        if (content.isEmpty()) return null;
        result.setMessage(text.toString());
        result.setMessageContentList(content);
        result.setAtUserList(mentions);
        return result;
    }

    static JSONArray segments(Object message) {
        if (message instanceof JSONArray array) return array;
        if (message instanceof List<?> list) return JSON.parseArray(JSON.toJSONString(list));
        JSONArray result = new JSONArray();
        if (message == null) return result;
        String raw = message.toString();
        if (raw.startsWith("[")) {
            try { return JSON.parseArray(raw); } catch (RuntimeException ignored) { }
        }
        Matcher matcher = CQ.matcher(raw);
        int from = 0;
        while (matcher.find()) {
            if (matcher.start() > from) result.add(segment("text", new JSONObject().fluentPut("text", unescape(raw.substring(from, matcher.start()), false))));
            JSONObject data = new JSONObject();
            String parameters = matcher.group(2);
            if (!parameters.isEmpty()) for (String part : parameters.substring(1).split(",")) {
                int equals = part.indexOf('=');
                if (equals > 0) data.put(part.substring(0, equals), unescape(part.substring(equals + 1), true));
            }
            result.add(segment(matcher.group(1), data));
            from = matcher.end();
        }
        if (from < raw.length()) result.add(segment("text", new JSONObject().fluentPut("text", unescape(raw.substring(from), false))));
        return result;
    }

    private static JSONObject segment(String type, JSONObject data) {
        return new JSONObject().fluentPut("type", type).fluentPut("data", data);
    }

    private static String unescape(String value, boolean parameter) {
        if (parameter) value = value.replace("&#44;", ",");
        return value.replace("&#91;", "[").replace("&#93;", "]").replace("&amp;", "&");
    }

    private static boolean isHttp(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
