package com.bb.bot.handler.news;

import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsStore;
import com.bb.bot.schedule.DailyNewsSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

/**
 * 每日资讯日报聊天命令：@机器人 发「日报 / 资讯日报 / 新闻日报」触发。
 *
 * <p>行为：今日日报已存在则直接回访问链接；不存在则即时生成后再回链接。
 * 不受 {@code news.enabled} 定时开关影响（手动命令始终可用）。</p>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "每日资讯日报")
public class DailyNewsHandler {

    @Autowired
    private BbReplies bbReplies;

    @Autowired
    private NewsStore newsStore;

    @Autowired
    private DailyNewsSchedule dailyNewsSchedule;

    @Autowired
    private NewsConfig newsConfig;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"日报", "资讯日报", "新闻日报"}, name = "查询每日资讯日报")
    public void daily(BbReceiveMessage msg) {
        try {
            String today = LocalDate.now().toString();
            DailyReport report = newsStore.getReport(today);

            if (report == null) {
                bbReplies.atText(msg, "📰 今日日报还没生成，正在为你生成，请稍候…");
                String url = dailyNewsSchedule.generateNow();
                if (url == null) {
                    bbReplies.atText(msg, "今日暂无新资讯，稍后再试～");
                    return;
                }
                report = newsStore.getReport(today);
            }

            int count = report == null ? 0 : report.totalCount();
            String full = NewsUrls.fullFor(newsConfig, today);
            bbReplies.atText(msg, "📰 今日资讯日报（精选 " + count + " 条）\n" + full);
        } catch (Exception e) {
            log.error("[news] 日报命令处理失败", e);
            bbReplies.atText(msg, "生成日报失败：" + e.getMessage());
        }
    }
}
