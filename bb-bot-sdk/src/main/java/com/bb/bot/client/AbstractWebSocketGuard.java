package com.bb.bot.client;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;

import java.net.URI;

/**
 * WebSocket 客户端守护基类。
 *
 * <p>抽取自 {@code BbWebSocketClient} 与 {@code QqWebSocketClient} 的 {@code connectLoop}
 * 共性：一个<strong>长生命周期、永不退出</strong>的守护线程，周期性地执行一次
 * {@link #handleTick()}（子类实现心跳/重连判断），并在两次之间 {@code sleep(interval())}。</p>
 *
 * <p>历史上出现过重连风暴导致 {@code OutOfMemoryError: unable to create new native thread}
 * （属于 {@link Error} 而非 {@link Exception}）或中断异常把守护线程打挂、之后再也不重连的问题。
 * 因此本基类保证：</p>
 * <ul>
 *   <li>{@link #handleTick()} 的异常用 {@link Throwable} 兜底，任何异常/错误都只记录、不退出循环；</li>
 *   <li>{@code sleep} 期间的 {@link InterruptedException} 不向上抛，仅清除中断标志后继续守护；</li>
 *   <li>提供 {@link #shouldReconnect()} + {@code minReconnectIntervalMs} 退避，供子类复用，
 *       避免握手未完成期间反复 {@code reconnect()} 引发线程风暴。</li>
 * </ul>
 *
 * @author ren
 */
@Slf4j
public abstract class AbstractWebSocketGuard extends WebSocketClient {

    /**
     * 守护线程名前缀（便于 jstack 定位），如 {@code "qq-ws-reconnect-"}。
     */
    private final String guardThreadPrefix;

    /**
     * 守护循环运行标志。生产路径恒为 {@code true}（等价于原 {@code while(true)}），
     * 仅供 {@link #stopGuard()} 在测试/优雅停机时退出循环，避免线程泄漏。
     * volatile：守护线程读、外部线程写，跨线程可见性必须保证。
     */
    private volatile boolean running = true;

    /**
     * 守护线程引用，{@link #startGuard()} 幂等保护用。
     */
    private Thread guardThread;

    protected AbstractWebSocketGuard(URI serverUri, String guardThreadPrefix) {
        super(serverUri);
        this.guardThreadPrefix = guardThreadPrefix;
    }

    /**
     * 守护线程一个周期的间隔（毫秒）。子类返回各自的 {@code CONNECT_INTERVAL}。
     */
    protected abstract long interval();

    /**
     * 守护线程一个周期要做的事：心跳 / 重连判断等。由子类实现。
     *
     * <p>实现体内可直接抛异常——基类会兜底捕获 {@link Throwable} 并记录，循环不会中断。</p>
     */
    protected abstract void handleTick();

    /**
     * 启动守护线程（守护线程、永不主动退出）。幂等：已在运行则直接返回。
     */
    protected final void startGuard() {
        if (guardThread != null && guardThread.isAlive()) {
            return;
        }
        guardThread = new Thread(this::guardLoop, guardThreadPrefix + threadNameSuffix());
        guardThread.setDaemon(true);
        guardThread.start();
    }

    /**
     * 守护线程名后缀，默认空。子类可覆写返回机器人名等便于定位。
     */
    protected String threadNameSuffix() {
        return "";
    }

    /**
     * 守护循环。等价于原 {@code while(true)}：每周期吞掉 {@link #handleTick()} 的任何
     * {@link Throwable}（含 {@link Error}），再 {@code sleep(interval())}（吞中断），永不退出。
     */
    private void guardLoop() {
        while (running) {
            try {
                handleTick();
            } catch (Throwable t) {
                //捕获 Throwable（含 OutOfMemoryError 等 Error），守护线程绝不能因此退出
                log.error("WebSocket客户端守护线程 tick 异常", t);
            }
            try {
                Thread.sleep(interval());
            } catch (InterruptedException e) {
                //底层重连会中断相关线程；不能因中断而杀死本守护线程，清除标志后继续
                Thread.interrupted();
            }
        }
    }

    /**
     * 停止守护循环（仅供测试 / 优雅停机使用，生产路径不调用）。
     */
    protected final void stopGuard() {
        running = false;
        if (guardThread != null) {
            guardThread.interrupt();
        }
    }

    /**
     * 是否需要发起重连的通用退避判断。
     *
     * <p>已连接（OPEN）或正在关闭（CLOSING）时不重连；其余状态
     * （NOT_YET_CONNECTED / CLOSED）可能正在握手，用最小间隔
     * {@code minReconnectIntervalMs} 兜底，防止连接建立期间被反复触发。</p>
     *
     * @param lastConnectAttemptAt   上一次发起连接/重连的时间戳
     * @param minReconnectIntervalMs 两次重连尝试之间的最小间隔（毫秒）
     */
    protected final boolean shouldReconnect(long lastConnectAttemptAt, long minReconnectIntervalMs) {
        if (isOpen() || isClosing()) {
            return false;
        }
        return System.currentTimeMillis() - lastConnectAttemptAt >= minReconnectIntervalMs;
    }
}
