package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 中央事件记录器：所有入站消息、出站消息、工具调用都通过这里落到 ai_memory_event。
 *
 * <p>核心 API：</p>
 * <ul>
 *   <li>{@link #recordInbound(BbReceiveMessage)} —— BbEventListener 调用，自动按消息内容预分类 kind</li>
 *   <li>{@link #recordOutbound(BbReceiveMessage, String, String, String)} —— Handler 发回复时调，显式 kind</li>
 *   <li>{@link #recordToolInvocation(String, String, String, String, String, String)} —— 工具调用摘要</li>
 * </ul>
 *
 * <p>Inbound 自动分类规则：</p>
 * <ul>
 *   <li>{@code agent } / {@code Agent } / {@code /agent } 开头 → kind=agent_cmd</li>
 *   <li>{@code /aiAgent.} 开头 → kind=admin_cmd</li>
 *   <li>其它 / 开头 → kind=admin_cmd</li>
 *   <li>其余 → kind=chat</li>
 * </ul>
 */
@Slf4j
@Component
public class MemoryEventRecorder {

    private static final ThreadLocal<String> OUTBOUND_KIND_OVERRIDE = new ThreadLocal<>();

    @Autowired
    private IAiMemoryEventService eventService;

    @Autowired
    private SessionTracker sessionTracker;

    /** 入站消息 → 自动分类落库，返回事件 id 便于关联回复 */
    public Long recordInbound(BbReceiveMessage msg) {
        if (msg == null) return null;
        try {
            String sessionId = sessionTracker.attachSessionId(msg.getUserId(), msg.getGroupId(), msg.getBotType());
            AiMemoryEvent e = new AiMemoryEvent();
            e.setSessionId(sessionId);
            e.setPlatform(msg.getBotType());
            e.setUserId(msg.getUserId());
            e.setGroupId(msg.getGroupId());
            e.setUserName(msg.getSender() == null ? null : msg.getSender().getNickname());
            e.setSource("user");
            e.setKind(classifyInbound(msg.getMessage()));
            e.setMessageId(msg.getMessageId());
            e.setText(msg.getMessage());
            e.setPayload(serializeContentList(msg.getMessageContentList()));
            e.setCreatedAt(LocalDateTime.now());
            eventService.save(e);
            return e.getId();
        } catch (Exception ex) {
            log.warn("recordInbound 失败（非致命）", ex);
            return null;
        }
    }

    /**
     * 把已记录的 inbound 事件降级为命令（kind=command）。命中关键字命令的消息不是普通聊天，
     * 降级后不再进入聊天上下文（loadChatContext 只取 chat / chat_reply），避免命令被当成待办重复处理。
     */
    public void markCommand(Long eventId) {
        if (eventId == null) return;
        try {
            AiMemoryEvent e = eventService.getById(eventId);
            if (e != null && "chat".equals(e.getKind())) {
                e.setKind("command");
                eventService.updateById(e);
            }
        } catch (Exception ex) {
            log.warn("markCommand 失败（非致命）eventId={}", eventId, ex);
        }
    }

    /** Bot 出站消息（聊天 / agent / admin / cron 回复）→ 落库 */
    public Long recordOutbound(BbReceiveMessage sourceMsg, String kind, String text, String messageIdHint) {
        if (sourceMsg == null) return null;
        try {
            String sessionId = sessionTracker.attachSessionId(sourceMsg.getUserId(), sourceMsg.getGroupId(), sourceMsg.getBotType());
            AiMemoryEvent e = new AiMemoryEvent();
            e.setSessionId(sessionId);
            e.setPlatform(sourceMsg.getBotType());
            e.setUserId(sourceMsg.getUserId());
            e.setGroupId(sourceMsg.getGroupId());
            e.setUserName("bot");
            e.setSource("bot");
            e.setKind(kind);
            e.setMessageId(messageIdHint);
            e.setText(text);
            e.setCreatedAt(LocalDateTime.now());
            eventService.save(e);
            return e.getId();
        } catch (Exception ex) {
            log.warn("recordOutbound 失败（非致命）", ex);
            return null;
        }
    }

    /** Bot 出站消息信封 → 落库。用于统一记录所有真正发到聊天窗口里的回复。 */
    public Long recordOutbound(BbSendMessage out, String defaultKind) {
        if (out == null) return null;
        List<BbMessageContent> contents = out.getMessageList();
        if (contents == null || contents.isEmpty()) return null;
        try {
            String sessionId = sessionTracker.attachSessionId(out.getUserId(), out.getGroupId(), out.getBotType());
            AiMemoryEvent e = new AiMemoryEvent();
            e.setSessionId(sessionId);
            e.setPlatform(out.getBotType());
            e.setUserId(out.getUserId());
            e.setGroupId(out.getGroupId());
            e.setUserName("bot");
            e.setSource("bot");
            e.setKind(currentOutboundKind(defaultKind));
            e.setMessageId(IdWorker.getIdStr());
            e.setText(renderText(contents));
            e.setPayload(serializeContentList(contents));
            e.setCreatedAt(LocalDateTime.now());
            eventService.save(e);
            return e.getId();
        } catch (Exception ex) {
            log.warn("recordOutbound(sendMessage) 失败（非致命）", ex);
            return null;
        }
    }

    /** 在当前线程内给下一次统一出站记录指定 kind，不影响实际消息发送协议。 */
    public void withOutboundKind(String kind, Runnable action) {
        if (action == null) return;
        String previous = OUTBOUND_KIND_OVERRIDE.get();
        if (StringUtils.isNotBlank(kind)) {
            OUTBOUND_KIND_OVERRIDE.set(kind);
        }
        try {
            action.run();
        } finally {
            if (previous == null) {
                OUTBOUND_KIND_OVERRIDE.remove();
            } else {
                OUTBOUND_KIND_OVERRIDE.set(previous);
            }
        }
    }

    /** 工具调用摘要事件（详情仍在 ai_tool_invocation_log） */
    public Long recordToolInvocation(String sessionId, String userId, String platform,
                                      String toolName, String argsJson, String status) {
        try {
            AiMemoryEvent e = new AiMemoryEvent();
            e.setSessionId(sessionId);
            e.setPlatform(platform);
            e.setUserId(userId);
            e.setSource("tool");
            e.setKind("tool_invocation");
            e.setText(toolName + " (" + status + ")");
            Map<String, Object> p = Map.of("tool", toolName, "args", argsJson, "status", status);
            e.setPayload(JSON.toJSONString(p));
            e.setCreatedAt(LocalDateTime.now());
            eventService.save(e);
            return e.getId();
        } catch (Exception ex) {
            log.warn("recordToolInvocation 失败（非致命）", ex);
            return null;
        }
    }

    /** 当前用户 + 群组的 session_id（不写入新事件，只查） */
    public String currentSessionId(String userId, String groupId, String platform) {
        return sessionTracker.attachSessionId(userId, groupId, platform);
    }

    /** Inbound 消息按内容前缀预分类。所有命令类不入聊天上下文。 */
    public static String classifyInbound(String text) {
        if (StringUtils.isBlank(text)) return "chat";
        String t = text.trim();
        if (t.startsWith("agent ") || t.startsWith("/agent ") || t.startsWith("Agent ")) {
            return "agent_cmd";
        }
        if (t.startsWith("/aiAgent.") || t.startsWith("aiAgent.")) {
            return "admin_cmd";
        }
        if (t.startsWith("/")) {
            return "admin_cmd";
        }
        return "chat";
    }

    private String serializeContentList(List<BbMessageContent> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            // 跳过本地图片这种大对象
            List<BbMessageContent> filtered = list.stream()
                    .filter(c -> !BbSendMessageType.LOCAL_IMAGE.equals(c.getType()))
                    .toList();
            return JSON.toJSONString(filtered);
        } catch (Exception e) {
            return null;
        }
    }

    private String currentOutboundKind(String defaultKind) {
        String override = OUTBOUND_KIND_OVERRIDE.get();
        return StringUtils.defaultIfBlank(override, StringUtils.defaultIfBlank(defaultKind, "handler_reply"));
    }

    private String renderText(List<BbMessageContent> contents) {
        if (contents == null || contents.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : contents) {
            if (c == null || c.getData() == null) {
                continue;
            }
            String type = c.getType();
            if (BbSendMessageType.TEXT.equals(type)) {
                sb.append(c.getData());
            } else if (BbSendMessageType.AT.equals(type)) {
                sb.append('@').append(c.getData()).append(' ');
            } else if (BbSendMessageType.NET_IMAGE.equals(type) || BbSendMessageType.LOCAL_IMAGE.equals(type)) {
                sb.append("[图片]");
            } else if (BbSendMessageType.NET_FILE.equals(type) || BbSendMessageType.LOCAL_FILE.equals(type)) {
                sb.append("[附件文件]");
            }
        }
        String text = sb.toString().trim();
        return StringUtils.isBlank(text) ? null : text;
    }
}
