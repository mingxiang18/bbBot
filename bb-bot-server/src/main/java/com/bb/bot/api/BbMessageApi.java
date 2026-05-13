package com.bb.bot.api;

import com.bb.bot.entity.bb.BbSendMessage;

/**
 * 发起动作请求Api
 * @author ren
 */
public interface BbMessageApi {

    /**
     * 发送消息
     *
     * @param bbSendMessage 发送的消息内容
     */
    public void sendMessage(BbSendMessage bbSendMessage);

    /**
     * 打开一次流式回复会话。
     *
     * <p>调用方负责往返回的 session 持续 appendDelta，最后 complete 或 fail。
     * 不同平台呈现策略不同：</p>
     * <ul>
     *   <li>Telegram / Discord / BB：edit-message 持续覆盖</li>
     *   <li>OneBot / QQ 官方：按句号 / 字符上限切段连发</li>
     *   <li>不支持的平台：缓冲到 complete() 一次性发出（FallbackMessageStreamSession）</li>
     * </ul>
     *
     * @param bbSendMessage 包含目标 channel / userId / groupId / config 等寻址信息的信封
     * @return 流式会话
     */
    MessageStreamSession startStream(BbSendMessage bbSendMessage);
}
