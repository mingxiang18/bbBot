package com.bb.bot.connection.qq;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.client.AbstractWebSocketGuard;
import com.bb.bot.common.util.LocalCacheUtils;
import com.bb.bot.entity.qq.QqCommonPayloadEntity;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QqWebSocketClient} 的 {@code handleTick}（心跳/重连）逻辑单测。
 *
 * <p>QQ 版相对 BB 版的差异是“已连接即发心跳”：{@code handleTick} 在 {@code isOpen()} 时
 * 发送 op=1 心跳（带最新 {@code qq.seq}），未连接且过退避间隔时 {@code reconnect()}。
 * 守护循环“永不退出 / 吞 Throwable / 退避阈值”由基类
 * {@link AbstractWebSocketGuard} 负责，已在其自身单测覆盖；此处只验证 QQ 子类的 tick 行为。</p>
 *
 * <p>用伪子类绕过真实网络：覆写 {@code connect()} 为空（拦掉父构造里的首次连接），
 * 覆写 {@code isOpen()}/{@code send()}/{@code reconnect()} 以可控驱动并记录调用。
 * 构造完成后立刻 {@code stopGuard()} 停掉 30s 守护线程，由测试手动调用 {@link TestableQqClient#tick()}。</p>
 */
class QqWebSocketClientTest {

    private static final URI DUMMY_URI = URI.create("ws://localhost:1");

    /**
     * 可测子类：手动驱动 tick，记录 send 的报文与 reconnect 次数，可切换 open 状态。
     */
    private static class TestableQqClient extends QqWebSocketClient {
        volatile boolean open = false;
        final List<String> sent = new ArrayList<>();
        final AtomicInteger reconnectCount = new AtomicInteger(0);

        TestableQqClient() {
            super("test-bot", null, null, null, DUMMY_URI);
            //构造里 startGuard() 起了 30s 守护线程；测试自己驱动 tick，先停掉避免干扰
            stopGuard();
        }

        /** 拦掉父构造内的首次 connect()，不发起真实网络连接。 */
        @Override
        public void connect() {
            //no-op
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void send(String text) {
            sent.add(text);
        }

        @Override
        public void reconnect() {
            reconnectCount.incrementAndGet();
        }

        /** 暴露 protected handleTick 供测试手动驱动一个周期。 */
        void tick() {
            handleTick();
        }
    }

    @Test
    void heartbeatSentWhenOpen() {
        TestableQqClient client = new TestableQqClient();
        LocalCacheUtils.setCacheObject("qq.seq", 4242);
        client.open = true;

        client.tick();

        //已连接：发一条心跳，不重连
        assertThat(client.reconnectCount.get()).isZero();
        assertThat(client.sent).hasSize(1);
        QqCommonPayloadEntity payload = JSON.parseObject(client.sent.get(0), QqCommonPayloadEntity.class);
        assertThat(payload.getOp()).isEqualTo(1);
        assertThat(payload.getD()).isEqualTo(4242);
    }

    @Test
    void heartbeatCarriesLatestSeqEachTick() {
        TestableQqClient client = new TestableQqClient();
        client.open = true;

        LocalCacheUtils.setCacheObject("qq.seq", 1);
        client.tick();
        LocalCacheUtils.setCacheObject("qq.seq", 2);
        client.tick();

        assertThat(client.sent).hasSize(2);
        assertThat(JSON.parseObject(client.sent.get(0), QqCommonPayloadEntity.class).getD()).isEqualTo(1);
        assertThat(JSON.parseObject(client.sent.get(1), QqCommonPayloadEntity.class).getD()).isEqualTo(2);
        assertThat(client.reconnectCount.get()).isZero();
    }

    @Test
    void reconnectWhenNotOpenAndPastBackoff() throws Exception {
        TestableQqClient client = new TestableQqClient();
        client.open = false;
        //把上次连接尝试时间推到很久以前，确保超过最小重连间隔
        setLastConnectAttempt(client, System.currentTimeMillis() - 60_000L);

        client.tick();

        //未连接且已过退避：重连，且不发心跳
        assertThat(client.reconnectCount.get()).isEqualTo(1);
        assertThat(client.sent).isEmpty();
    }

    @Test
    void noReconnectWhenNotOpenButWithinBackoff() throws Exception {
        TestableQqClient client = new TestableQqClient();
        client.open = false;
        //刚刚才尝试过连接，未达最小重连间隔 → 不重连
        setLastConnectAttempt(client, System.currentTimeMillis());

        client.tick();

        assertThat(client.reconnectCount.get()).isZero();
        assertThat(client.sent).isEmpty();
    }

    @Test
    void reconnectUpdatesLastAttemptSoNextTickBacksOff() throws Exception {
        TestableQqClient client = new TestableQqClient();
        client.open = false;
        setLastConnectAttempt(client, System.currentTimeMillis() - 60_000L);

        //第一次：满足退避 → 重连并刷新 lastConnectAttemptAt 为“现在”
        client.tick();
        //紧接着第二次：距上次重连不到最小间隔 → 不再重连
        client.tick();

        assertThat(client.reconnectCount.get()).isEqualTo(1);
    }

    /** 用反射设置私有 lastConnectAttemptAt，以确定性地驱动退避分支。 */
    private static void setLastConnectAttempt(QqWebSocketClient client, long value) throws Exception {
        var f = QqWebSocketClient.class.getDeclaredField("lastConnectAttemptAt");
        f.setAccessible(true);
        f.setLong(client, value);
    }
}
