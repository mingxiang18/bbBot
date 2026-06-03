package com.bb.bot.schedule;

/**
 * 已有日报生成任务在执行时，再次触发抛出本异常（生成互斥保护）。
 *
 * <p>由 {@link DailyNewsSchedule#generateNow()} 在抢锁失败时抛出，
 * 管理端点据此返回 409 Conflict，而非 500。</p>
 */
public class NewsGenerationBusyException extends RuntimeException {

    public NewsGenerationBusyException(String message) {
        super(message);
    }
}
