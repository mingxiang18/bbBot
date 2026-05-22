package com.bb.bot.aiAgent.tools;

import java.io.File;

/**
 * 工具向「当前对话」回传内容的出站通道抽象。
 *
 * <p>背景：send_file 这类工具需要把产物主动发回触发本轮 agent 的那个会话（IM 群/私聊），
 * 但工具方法在 {@code AiToolExecutor} 的线程池里反射执行，签名里没有会话/连接信息。
 * 由各平台 handler（如 BbAiChatHandler）构造一个本接口的实现，经 {@link AgentReplyContext}
 * 注入到工具线程；工具只面向本抽象，core/tools 包不依赖具体平台类型。</p>
 */
public interface AgentReplySink {

    /** 当前会话端是否支持接收文件附件（不支持时工具应改用文字告知，而非硬发）。 */
    boolean fileSupported();

    /**
     * 把一个本地文件作为附件发回当前会话。
     *
     * @param file     本地文件（由调用方保证已落在调用者文件空间内）
     * @param fileName 展示用文件名；null 则用 file 的名字
     */
    void sendFile(File file, String fileName);
}
