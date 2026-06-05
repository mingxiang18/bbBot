package com.bb.bot.dispatcher;

import com.bb.bot.aiAgent.fs.InboundImageStore;
import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.diagnostics.MessageTraceRecorder;
import com.bb.bot.diagnostics.TraceContext;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Slf4j
@Component
public class BbEventListener {

    @Autowired
    private BbEventDispatcher bbEventDispatcher;

    @Autowired
    private MemoryEventRecorder memoryEventRecorder;

    @Autowired
    private InboundMessageDeduplicator inboundMessageDeduplicator;

    @Autowired
    private MessageTraceRecorder messageTraceRecorder;

    @Autowired
    private InboundImageStore inboundImageStore;

    @EventListener
    public void listenMessageEvent(BbReceiveMessage bbReceiveMessage) {
        // 全链路 traceId：同一条消息在去重 / 分发 / 决策 / 发送各处的日志共享此 id，便于事后串联排查。
        String traceId = TraceContext.start(bbReceiveMessage.getMessageId());
        try {
            log.info("收到入站消息 platform={} type={} group={} user={} messageId={} text={}",
                    bbReceiveMessage.getBotType(), bbReceiveMessage.getMessageType(),
                    bbReceiveMessage.getGroupId(), bbReceiveMessage.getUserId(),
                    bbReceiveMessage.getMessageId(), abbreviate(bbReceiveMessage.getMessage()));

            // 记录处理轨迹（内存环形缓冲），供 owner 对话式自查
            messageTraceRecorder.onInbound(traceId, bbReceiveMessage);

            // 入站幂等去重：QQ webhook 在被动回复超时后会重推同一条消息（msg_id 相同），
            // 没有这道拦截就会处理两遍、给用户发两张一模一样的图。放在最前面，连记忆落库和分发都不重复。
            if (!inboundMessageDeduplicator.firstSeen(bbReceiveMessage.getMessageId())) {
                log.warn("重复入站消息，已跳过 messageId={}", bbReceiveMessage.getMessageId());
                messageTraceRecorder.onDuplicate(traceId);
                return;
            }

            // 入站图片规范化：localImage/netImage → 按内容哈希落盘去重 + 改写成 netImage(ref)。
            // 放在去重之后、recordInbound 之前：重复消息不重复规范化；且 localImage 若不在此处转成
            // netImage(ref)，会被 serializeContentList 过滤、历史拿不到图片 ref。
            inboundImageStore.normalize(bbReceiveMessage.getMessageContentList());

            // M8.2：每条入站消息先落 ai_memory_event（自动按内容预分类 kind）
            // 不阻塞 dispatcher：recorder 内部已 try/catch 兜底
            Long inboundEventId = memoryEventRecorder.recordInbound(bbReceiveMessage);

            //执行消息事件分发
            boolean commandMatched = bbEventDispatcher.handleMessage(bbReceiveMessage);

            // 命中关键字命令（如 /我的用量、获取聊天线索 等，无论带不带斜杠）→ 把该 inbound 事件
            // 从 chat 降级为 command，避免命令污染聊天上下文、被 AI 当成待办重复处理一遍
            if (commandMatched) {
                memoryEventRecorder.markCommand(inboundEventId);
            }
        } finally {
            // 同步段结束即清理；异步 handler 线程的 traceId 由线程池 TaskDecorator 独立维护。
            TraceContext.clear();
        }
    }

    /** 日志里消息正文截断，避免长文/图片 base64 刷屏。 */
    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…(" + text.length() + ")";
    }

}
