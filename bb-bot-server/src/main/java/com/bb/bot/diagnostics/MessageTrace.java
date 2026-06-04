package com.bb.bot.diagnostics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条入站消息的处理轨迹：从「收到」到「最终发出/跳过」的关键节点。
 *
 * <p>由 {@link MessageTraceRecorder} 在链路各点填充，常驻内存环形缓冲，供 owner 通过
 * {@code self_check} / {@code trace_message} 工具即时自查「这条消息为什么没回」。</p>
 *
 * <p>字段用 volatile：一条消息的轨迹通常在单线程内顺序填充，但发送出口可能在另一线程，
 * 故用 volatile 保证可见性；并发只在 {@link #appendHandler} 处用 synchronized 兜底。</p>
 */
public class MessageTrace {

    /** 决策：直接回复（私聊/@我）。 */
    public static final String DECISION_REPLY_DIRECT = "REPLY_DIRECT";
    /** 决策：群聊概率命中，回复。 */
    public static final String DECISION_REPLY = "REPLY";
    /** 决策：跳过。 */
    public static final String DECISION_SKIP = "SKIP";

    /** 发送结果：成功。 */
    public static final String REPLY_SENT = "SENT";
    /** 发送结果：失败。 */
    public static final String REPLY_FAILED = "FAILED";

    private final String traceId;
    private final LocalDateTime receivedAt;
    private final String platform;
    private final String messageType;
    private final String groupId;
    private final String userId;
    private final String userName;
    private final String textPreview;

    private volatile boolean duplicate;
    private volatile String handler;
    private volatile String decision;
    private volatile String skipReason;
    private volatile String replyStatus;
    private volatile String replyVia;
    private volatile String replyError;
    private volatile LocalDateTime finishedAt;

    public MessageTrace(String traceId, LocalDateTime receivedAt, String platform, String messageType,
                        String groupId, String userId, String userName, String textPreview) {
        this.traceId = traceId;
        this.receivedAt = receivedAt;
        this.platform = platform;
        this.messageType = messageType;
        this.groupId = groupId;
        this.userId = userId;
        this.userName = userName;
        this.textPreview = textPreview;
    }

    synchronized void appendHandler(String handlerName) {
        this.handler = (this.handler == null) ? handlerName : this.handler + "," + handlerName;
    }

    void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    void setDecision(String decision) { this.decision = decision; }
    void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    void setReply(String status, String via, String error) {
        this.replyStatus = status;
        this.replyVia = via;
        this.replyError = error;
        this.finishedAt = LocalDateTime.now();
    }

    public String getTraceId() { return traceId; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public String getUserId() { return userId; }
    public boolean isDuplicate() { return duplicate; }
    public String getDecision() { return decision; }
    public String getSkipReason() { return skipReason; }
    public String getReplyStatus() { return replyStatus; }
    public String getTextPreview() { return textPreview; }

    /** 是否「最终没给用户发出任何回复」：被去重、被跳过、或发送失败。 */
    public boolean isSilent() {
        return duplicate
                || DECISION_SKIP.equals(decision)
                || REPLY_FAILED.equals(replyStatus);
    }

    /** 转为工具返回用的有序 Map（null 字段省略，保持精简）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", traceId);
        m.put("receivedAt", receivedAt == null ? null : receivedAt.toString());
        put(m, "platform", platform);
        put(m, "type", messageType);
        put(m, "group", groupId);
        put(m, "user", userId);
        put(m, "userName", userName);
        put(m, "text", textPreview);
        if (duplicate) {
            m.put("duplicate", true);
        }
        put(m, "handler", handler);
        put(m, "decision", decision);
        put(m, "skipReason", skipReason);
        put(m, "replyStatus", replyStatus);
        put(m, "replyVia", replyVia);
        put(m, "replyError", replyError);
        if (receivedAt != null && finishedAt != null) {
            m.put("costMs", Duration.between(receivedAt, finishedAt).toMillis());
        }
        return m;
    }

    private static void put(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }
}
