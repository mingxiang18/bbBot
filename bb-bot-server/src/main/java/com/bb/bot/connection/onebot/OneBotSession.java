package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 单个 OneBot 账号的连接及 API 请求状态。
 *
 * <p>WebSocket 读线程只负责接收消息和完成 API 回执，业务处理放到独立队列中。
 * 业务同步发送回复、等待回执时，不会阻塞负责接收该回执的线程。</p>
 */
public final class OneBotSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OneBotSession.class);

    private static final int MAX_PENDING_REQUESTS = 256;
    private static final int INBOUND_QUEUE_CAPACITY = 256;
    private static final long MIN_API_TIMEOUT_MS = 100;
    private static final long MAX_API_TIMEOUT_MS = 60_000;

    /** 请求绑定到发起时的连接，旧连接的迟到回执不能完成新连接的请求。 */
    private record Pending(WebSocket socket, CompletableFuture<JSONObject> future) {
    }

    /** echo → 等待中的请求；回执、超时和断线时均通过条件删除避免重复完成。 */
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    /** 按接收顺序分发业务消息，并限制积压数量。 */
    private final ThreadPoolExecutor inbound =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(INBOUND_QUEUE_CAPACITY),
                    task -> {
                        Thread thread = new Thread(task, "onebot-inbound");
                        thread.setDaemon(true);
                        return thread;
                    });

    private volatile WebSocket connection;

    /** 绑定新连接，同时释放旧连接上仍在等待的请求。 */
    public synchronized void bind(WebSocket socket) {
        WebSocket oldConnection = connection;
        connection = socket;
        if (oldConnection != null && oldConnection != socket) {
            disconnected(oldConnection);
            oldConnection.close(1000, "OneBot connection replaced");
        }
    }

    public WebSocket connection() {
        return connection;
    }

    /** 只清理断开连接所属的状态，避免旧连接关闭时清空刚建立的新连接。 */
    public synchronized void disconnected(WebSocket socket) {
        if (connection == socket) {
            connection = null;
        }
        pending.forEach(
                (echo, request) -> {
                    if (request.socket() == socket && pending.remove(echo, request)) {
                        request.future()
                                .completeExceptionally(
                                        new IllegalStateException("OneBot connection closed"));
                    }
                });
    }

    /**
     * 在读线程中匹配 API 回执。
     *
     * @return true 表示该帧是 API 回执，不应再作为聊天事件分发
     */
    public boolean response(WebSocket socket, JSONObject response) {
        if (!response.containsKey("retcode")) {
            return false;
        }

        String echo = response.getString("echo");
        Pending request = echo == null ? null : pending.get(echo);
        if (request != null && request.socket() == socket && pending.remove(echo, request)) {
            request.future().complete(response);
        } else if (response.getIntValue("retcode") != 0) {
            log.warn("Unmatched OneBot API failure retcode={}", response.getInteger("retcode"));
        }
        return true;
    }

    /** 发送 API 请求并等待网关确认；失败或超时向调用方抛错，不能当成发送成功。 fallback 用于兼容消息信封中保存的连接，优先使用当前会话的有效连接。 */
    public JSONObject call(String action, Object params, WebSocket fallback, long timeoutMs) {
        WebSocket socket = connection;
        if (socket == null) {
            socket = fallback;
        }
        if (socket == null || !socket.isOpen()) {
            throw new IllegalStateException("OneBot is disconnected");
        }
        if (pending.size() >= MAX_PENDING_REQUESTS) {
            throw new IllegalStateException("Too many pending OneBot requests");
        }

        String echo = UUID.randomUUID().toString();
        Pending request = new Pending(socket, new CompletableFuture<>());
        pending.put(echo, request);
        try {
            Map<String, Object> payload = Map.of("action", action, "params", params, "echo", echo);
            socket.send(JSON.toJSONString(payload));

            long boundedTimeoutMs =
                    Math.max(MIN_API_TIMEOUT_MS, Math.min(timeoutMs, MAX_API_TIMEOUT_MS));
            JSONObject response = request.future().get(boundedTimeoutMs, TimeUnit.MILLISECONDS);
            if (!Integer.valueOf(0).equals(response.getInteger("retcode"))) {
                throw new IllegalStateException(
                        "OneBot " + action + " failed: retcode=" + response.getInteger("retcode"));
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OneBot request interrupted", e);
        } catch (TimeoutException e) {
            // 超时只代表没收到确认，QQ 可能已经收到消息；自动重发会造成重复发送。
            throw new IllegalStateException("OneBot " + action + " acknowledgement timed out", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "OneBot " + action + " connection failed", e.getCause());
        } finally {
            pending.remove(echo, request);
        }
    }

    /** 业务异常在队列线程中记录，不影响 WebSocket 读线程继续接收回执。 */
    public void dispatch(Runnable task) {
        inbound.execute(
                () -> {
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        log.error("OneBot inbound processing failed", e);
                    }
                });
    }

    @Override
    public void close() {
        WebSocket socket = connection;
        if (socket != null) {
            disconnected(socket);
        }
        inbound.shutdownNow();
    }
}
