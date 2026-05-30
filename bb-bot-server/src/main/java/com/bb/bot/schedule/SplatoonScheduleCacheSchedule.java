package com.bb.bot.schedule;

import com.bb.bot.handler.splatoon.SplatoonScheduleCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 斯普拉遁对战 / 打工日程图缓存的定时刷新任务。
 *
 * <p>固定每 6 小时拉一次 splatoon3.ink 的 schedules.json 并预渲染全部单时段落盘；
 * 启动就绪后立即预热一次（异步，不阻塞启动），避免等到第一个 6h 周期。</p>
 *
 * <p>一次拉取对战覆盖未来 24h、打工覆盖约 8 天，6h 间隔下缓存窗口始终含「当前时段」。
 * 拉取失败只记录日志，不影响读取——读取侧（{@link SplatoonScheduleCache}）会实时兜底。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class SplatoonScheduleCacheSchedule {

    @Autowired
    private SplatoonScheduleCache splatoonScheduleCache;

    /** 固定每 6 小时刷新一次日程缓存。 */
    @Scheduled(cron = "${splatoon.scheduleCache.cron:0 0 0/6 * * *}")
    public void refresh() {
        try {
            splatoonScheduleCache.refresh();
        } catch (Exception e) {
            log.error("斯普拉遁日程缓存定时刷新失败", e);
        }
    }

    /** 启动就绪后异步预热一次，让首个请求也能命中缓存。 */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        log.info("斯普拉遁日程缓存启动预热开始");
        try {
            splatoonScheduleCache.refresh();
        } catch (Exception e) {
            log.error("斯普拉遁日程缓存启动预热失败（不影响启动，读取时会实时兜底）", e);
        }
    }
}
