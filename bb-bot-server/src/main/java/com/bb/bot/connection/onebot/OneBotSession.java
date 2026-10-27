package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/** Per-bot state. Complete API responses on the reader thread; run business
 * handlers separately so synchronous replies cannot deadlock that reader. */
public final class OneBotSession implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(OneBotSession.class);
    private record Pending(WebSocket socket, CompletableFuture<JSONObject> future) { }
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor inbound = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256), task -> {
                Thread t = new Thread(task, "onebot-inbound"); t.setDaemon(true); return t;
            });
    private volatile WebSocket connection;

    public synchronized void bind(WebSocket socket) {
        WebSocket old = connection;
        connection = socket;
        if (old != null && old != socket) {
            disconnected(old);
            old.close(1000, "OneBot connection replaced");
        }
    }

    public WebSocket connection() { return connection; }

    public synchronized void disconnected(WebSocket socket) {
        if (connection == socket) connection = null;
        pending.forEach((echo, call) -> {
            if (call.socket() == socket && pending.remove(echo, call))
                call.future().completeExceptionally(new IllegalStateException("OneBot connection closed"));
        });
    }

    public boolean response(WebSocket socket, JSONObject response) {
        if (!response.containsKey("retcode")) return false;
        String echo = response.getString("echo");
        Pending call = echo == null ? null : pending.get(echo);
        if (call != null && call.socket() == socket && pending.remove(echo, call)) {
            call.future().complete(response);
        } else if (response.getIntValue("retcode") != 0) {
            log.warn("Unmatched OneBot API failure retcode={}", response.getInteger("retcode"));
        }
        return true;
    }

    public JSONObject call(String action, Object params, WebSocket fallback, long timeoutMs) {
        WebSocket socket = connection;
        if (socket == null) socket = fallback;
        if (socket == null || !socket.isOpen()) throw new IllegalStateException("OneBot is disconnected");
        if (pending.size() >= 256) throw new IllegalStateException("Too many pending OneBot requests");
        String echo = UUID.randomUUID().toString();
        Pending call = new Pending(socket, new CompletableFuture<>());
        pending.put(echo, call);
        try {
            socket.send(JSON.toJSONString(Map.of("action", action, "params", params, "echo", echo)));
            JSONObject result = call.future().get(Math.max(100, Math.min(timeoutMs, 60000)), TimeUnit.MILLISECONDS);
            if (!Integer.valueOf(0).equals(result.getInteger("retcode")))
                throw new IllegalStateException("OneBot " + action + " failed: retcode=" + result.getInteger("retcode"));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OneBot request interrupted", e);
        } catch (TimeoutException e) {
            // Never retry a timed-out send: it might already have reached QQ.
            throw new IllegalStateException("OneBot " + action + " acknowledgement timed out", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("OneBot " + action + " connection failed", e.getCause());
        } finally { pending.remove(echo, call); }
    }

    public void dispatch(Runnable task) {
        inbound.execute(() -> {
            try { task.run(); }
            catch (RuntimeException e) { log.error("OneBot inbound processing failed", e); }
        });
    }

    @Override public void close() {
        WebSocket socket = connection;
        if (socket != null) disconnected(socket);
        inbound.shutdownNow();
    }
}
