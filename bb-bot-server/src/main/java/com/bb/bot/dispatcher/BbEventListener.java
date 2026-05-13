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
        memoryEventRecorder.recordInbound(bbReceiveMessage);

        //执行消息事件分发
        bbEventDispatcher.handleMessage(bbReceiveMessage);
    }

}
