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

/** OneBot 入站帧处理：先分流 API 回执，再把聊天事件转换成 BB 通用消息。 */
@Slf4j
public final class OneBotMessageHandler {

    private static final Pattern CQ = Pattern.compile("\\[CQ:([a-zA-Z0-9_]+)((?:,[^\\]]*)?)\\]");

    private OneBotMessageHandler() {}

    public static void handleMessage(
            String name,
            OnebotConfig config,
            WebSocket socket,
            String raw,
            ApplicationEventPublisher publisher) {
        try {
            JSONObject event = JSON.parseObject(raw);
            if (event == null || config.getSession().response(socket, event)) {
                return;
            }
            // 连接替换后，旧连接可能还有迟到消息，不再分发到业务层。
            if (config.getSession().connection() != socket) {
                return;
            }

            BbReceiveMessage message = parseMessage(config, socket, event);
            if (message != null) {
                config.getSession().dispatch(() -> publisher.publishEvent(message));
            }
        } catch (RuntimeException e) {
            // 原始帧可能包含私人聊天、图片数据或临时凭据，日志仅记录异常类型。
            log.warn("OneBot {} rejected inbound frame: {}", name, e.getClass().getSimpleName());
        }
    }

    /** 仅接受本账号的群聊/私聊消息，并排除机器人自身消息。 */
    public static BbReceiveMessage parseMessage(
            OnebotConfig config, WebSocket socket, JSONObject event) {
        if (!"message".equals(event.getString("post_type"))) {
            return null;
        }
        String messageType = event.getString("message_type");
        if (!MessageType.GROUP.equals(messageType) && !MessageType.PRIVATE.equals(messageType)) {
            return null;
        }

        String selfId = event.getString("self_id");
        if (StringUtils.isNotBlank(config.getQq())
                && StringUtils.isNotBlank(selfId)
                && !config.getQq().equals(selfId)) {
            return null;
        }
        if (StringUtils.isBlank(config.getQq()) && StringUtils.isNotBlank(selfId)) {
            config.setQq(selfId);
        }

        String userId = event.getString("user_id");
        if (StringUtils.isBlank(userId)
                || userId.equals(StringUtils.defaultIfBlank(selfId, config.getQq()))) {
            return null;
        }
        String groupId = event.getString("group_id");
        if (MessageType.GROUP.equals(messageType) && StringUtils.isBlank(groupId)) {
            return null;
        }

        JSONObject sender = event.getJSONObject("sender");
        String nickname =
                sender == null
                        ? userId
                        : StringUtils.firstNonBlank(
                                sender.getString("card"), sender.getString("nickname"), userId);
        BbReceiveMessage message = new BbReceiveMessage();
        message.setBotType(BotType.ONEBOT);
        message.setConfig(config);
        message.setWebSocket(socket);
        message.setMessageType(messageType);
        message.setUserId(userId);
        message.setSender(new MessageUser(userId, nickname));
        message.setGroupId(MessageType.GROUP.equals(messageType) ? groupId : null);

        // 保留网关原始 ID，供 reply/get_msg 使用；账号与会话隔离由去重器负责。
        message.setMessageId(
                StringUtils.defaultIfBlank(
                        event.getString("message_id"), UUID.randomUUID().toString()));
        Long sendTime = event.getLong("time");
        if (sendTime != null && sendTime > 0) {
            message.setSendTime(
                    Instant.ofEpochSecond(sendTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime());
        }

        List<BbMessageContent> contents = new ArrayList<>();
        List<MessageUser> mentions = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Object item : segments(event.get("message"))) {
            if (!(item instanceof JSONObject segment)) {
                continue;
            }
            JSONObject data = segment.getJSONObject("data");
            if (data == null) {
                continue;
            }
            appendContent(config, segment.getString("type"), data, contents, mentions, text);
        }

        if (contents.isEmpty()) {
            return null;
        }
        message.setMessage(text.toString());
        message.setMessageContentList(contents);
        message.setAtUserList(mentions);
        return message;
    }

    /** 不支持的片段单独忽略，不影响同一条消息中的文字、图片等已支持内容。 */
    private static void appendContent(
            OnebotConfig config,
            String type,
            JSONObject data,
            List<BbMessageContent> contents,
            List<MessageUser> mentions,
            StringBuilder text) {
        String value;
        switch (StringUtils.defaultString(type)) {
            case "text" -> {
                value = data.getString("text");
                if (value != null) {
                    text.append(value);
                    contents.add(BbMessageContent.buildTextContent(value));
                }
            }
            case "at" -> {
                value = data.getString("qq");
                if (StringUtils.isNotBlank(value)) {
                    mentions.add(new MessageUser(value, value.equals(config.getQq())));
                    contents.add(BbMessageContent.buildAtMessageContent(value));
                }
            }
            case "image" -> {
                value = StringUtils.firstNonBlank(data.getString("url"), data.getString("file"));
                if (isHttp(value)) {
                    contents.add(BbMessageContent.buildNetImageMessageContent(value));
                }
            }
            case "reply" -> {
                value = data.getString("id");
                if (StringUtils.isNotBlank(value)) {
                    contents.add(BbMessageContent.buildReplyMessageContent(value));
                }
            }
            case "file" -> {
                value = data.getString("url");
                if (isHttp(value)) {
                    contents.add(
                            BbMessageContent.buildNetFileMessageContent(
                                    value, data.getString("name")));
                }
            }
            default -> {
                // 表情、语音等未适配片段暂不转换，保留同一消息中的其它有效片段。
            }
        }
    }

    /** 兼容数组消息、序列化后的 JSON 数组，以及旧网关的 CQ 码字符串。 */
    static JSONArray segments(Object message) {
        if (message instanceof JSONArray array) {
            return array;
        }
        if (message instanceof List<?> list) {
            return JSON.parseArray(JSON.toJSONString(list));
        }
        if (message == null) {
            return new JSONArray();
        }

        String raw = message.toString();
        if (raw.startsWith("[")) {
            try {
                return JSON.parseArray(raw);
            } catch (RuntimeException ignored) {
                // 也可能是以左括号开头的普通文字，继续按 CQ 字符串解析。
            }
        }
        return parseCqSegments(raw);
    }

    private static JSONArray parseCqSegments(String raw) {
        JSONArray result = new JSONArray();
        Matcher matcher = CQ.matcher(raw);
        int textStart = 0;
        while (matcher.find()) {
            if (matcher.start() > textStart) {
                result.add(textSegment(raw.substring(textStart, matcher.start())));
            }

            JSONObject data = new JSONObject();
            String parameters = matcher.group(2);
            if (!parameters.isEmpty()) {
                for (String parameter : parameters.substring(1).split(",")) {
                    int separator = parameter.indexOf('=');
                    if (separator > 0) {
                        data.put(
                                parameter.substring(0, separator),
                                unescape(parameter.substring(separator + 1), true));
                    }
                }
            }
            result.add(segment(matcher.group(1), data));
            textStart = matcher.end();
        }
        if (textStart < raw.length()) {
            result.add(textSegment(raw.substring(textStart)));
        }
        return result;
    }

    private static JSONObject textSegment(String text) {
        return segment("text", new JSONObject().fluentPut("text", unescape(text, false)));
    }

    private static JSONObject segment(String type, JSONObject data) {
        return new JSONObject().fluentPut("type", type).fluentPut("data", data);
    }

    /** 逗号仅在 CQ 参数内转义；最后还原 &amp;，避免对原文进行二次解码。 */
    private static String unescape(String value, boolean parameter) {
        if (parameter) {
            value = value.replace("&#44;", ",");
        }
        return value.replace("&#91;", "[").replace("&#93;", "]").replace("&amp;", "&");
    }

    private static boolean isHttp(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
