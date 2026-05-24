package com.bb.bot.dispatcher;

import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Component
public class BbEventListener {

    @Autowired
    private BbEventDispatcher bbEventDispatcher;

    @Autowired
    private MemoryEventRecorder memoryEventRecorder;

    @EventListener
    public void listenMessageEvent(BbReceiveMessage bbReceiveMessage) {
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
    }

}
