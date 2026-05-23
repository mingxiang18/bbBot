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

    /**
     * 当前会话端是否支持接收内联图片。图片是 IM 基础内容类型，默认不支持仅因为部分
     * 通道（如 cron 派活）没有可回传的会话。交互式会话的实现应覆盖为 true。
     */
    default boolean imageSupported() {
        return false;
    }

    /**
     * 把一个本地图片作为内联图片发回当前会话（区别于 {@link #sendFile}：图直接展示，
     * 不走文件附件能力位）。供 splatoon 日程图等「工具产出图片」的场景用。
     *
     * @param image 本地图片文件
     */
    default void sendImage(File image) {
        throw new UnsupportedOperationException("当前会话通道不支持发送图片");
    }
}
