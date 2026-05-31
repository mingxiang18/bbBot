package com.bb.bot.schedule;

import com.bb.bot.config.BotConfig;
import com.bb.bot.config.QqConfig;
import com.bb.bot.connection.qq.QqApiCaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * QQ access_token 提前刷新任务。
 *
 * <p>原本 token 缓存过期后，由下一条用户消息在请求热路径上「现拉」（同步 +约2s），把被动回复挤出
 * QQ 的 ~5 秒窗口 → QQ 重推同一条消息 → 发两张一模一样的图。这里改为<strong>后台提前刷新</strong>：
 * 每分钟检查所有启用的 QQ 配置，临近过期就先换新 token（官方过期前 60s 内重拉旧 token 仍有效，
 * 故可无缝衔接），让请求热路径永远拿到热 token、不再同步现拉。启动就绪后再预热一次。</p>
 *
 * <p>幂等去重（{@code InboundMessageDeduplicator}）是「两张图」的根治；本任务是消除诱因的延迟优化，二者互补。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class QqTokenRefreshSchedule {

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private QqApiCaller qqApiCaller;

    /** 每分钟检查一次，临近过期才真正请求换新（未临近则跳过，不发请求）。 */
    @Scheduled(fixedDelayString = "${qq.tokenRefresh.fixedDelayMs:60000}")
    public void refresh() {
        refreshAll(false);
    }

    /** 启动就绪后异步预热各 token，让首条消息也命中热 token，不阻塞启动。 */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        log.info("QQ access_token 启动预热开始");
        refreshAll(true);
    }

    /**
     * 遍历所有启用的 QQ 配置刷新 token。
     * @param force true=无条件刷新（预热）；false=仅临近过期时刷新（定时巡检）
     */
    private void refreshAll(boolean force) {
        if (botConfig.getQq() == null) {
            return;
        }
        for (QqConfig qqConfig : botConfig.getQq().values()) {
            if (qqConfig == null || !qqConfig.isEnable()) {
                continue;
            }
            try {
                if (force || qqApiCaller.nearExpiry(qqConfig)) {
                    qqApiCaller.refreshToken(qqConfig);
                }
            } catch (Exception e) {
                log.error("QQ access_token 提前刷新失败，appId={}", qqConfig.getAppId(), e);
            }
        }
    }
}
