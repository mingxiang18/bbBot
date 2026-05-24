package com.bb.bot.common.util.aiChat.provider;

import java.util.Map;

/**
 * 极简 HTTP 调用 SPI：把 provider 与具体 HTTP 客户端解耦，便于在单测里塞假实现。
 *
 * @author ren
 */
public interface HttpExchanger {

    /**
     * POST JSON。返回原始响应（含状态码）；调用方负责按状态码分类处理。
     * 网络层面的异常（连接失败、超时）应抛出 {@link java.io.IOException} 或运行时异常，
     * 由调用方决定是否归为可重试。
     */
    HttpResponse post(String url, Map<String, String> headers, String jsonBody) throws Exception;

    record HttpResponse(int status, String body) {}
}
