package com.bb.bot.aiAgent.cron;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.core.AiToolExecutor;
import com.bb.bot.aiAgent.core.AiToolRegistry;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.StreamHandler;
import com.bb.bot.common.util.aiChat.provider.ToolCall;
import com.bb.bot.common.util.aiChat.provider.ToolLoopExecutor;
import com.bb.bot.config.BotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.aiAgent.entity.AiCronTask;
import com.bb.bot.database.aiAgent.service.IAiCronTaskService;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 每分钟扫描 ai_cron_task，对到点任务跑一遍 agent 循环 + 流式推回目标频道。
 *
 * <p>到点判断：上次执行时间 → 下一个 cron 时刻 ≤ 现在 → 触发。
 * 启动后第一次扫描跳过历史窗口（避免重启后把过去几天的任务全补一次）。</p>
 *
 * <p>支持的平台：</p>
 * <ul>
 *   <li>TELEGRAM —— REST 完全可用</li>
 *   <li>DISCORD —— JDA 单例可用</li>
 *   <li>ONEBOT / BB —— 需活跃 WebSocket，MVP 阶段不保证（active 时尽力，否则跳过）</li>
 *   <li>QQ —— 需 QqConfig + 在线 token，MVP 阶段尽力</li>
 * </ul>
 */
@Slf4j
@Component
public class AiCronScheduler {

    @Autowired
    private IAiCronTaskService cronTaskService;

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private ToolLoopExecutor toolLoopExecutor;

    @Autowired
    private AiToolRegistry toolRegistry;

    @Autowired
    private AiToolExecutor toolExecutor;

    @Value("${aiAgent.cron.systemPrompt:你是 bbBot 定时任务 Agent。" +
            "用户提前安排了任务，到点后由你执行。优先调用已注册工具完成，简明扼要回复结果。}")
    private String systemPrompt;

    @Value("${aiAgent.cron.maxSteps:10}")
    private int maxSteps;

    private final ExecutorService cronPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ai-cron-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    private LocalDateTime startupAt = LocalDateTime.now();

    /** 每 30 秒扫一次（cron 最小粒度为分钟，30s 周期足够不漏） */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void tick() {
        try {
            List<AiCronTask> tasks = cronTaskService.list(new LambdaQueryWrapper<AiCronTask>()
                    .eq(AiCronTask::getEnabled, true));
            LocalDateTime now = LocalDateTime.now();
            List<AiCronTask> dueTasks = new ArrayList<>();
            for (AiCronTask t : tasks) {
                if (isDue(t, now)) {
                    dueTasks.add(t);
                }
            }
            for (AiCronTask t : dueTasks) {
                cronPool.submit(() -> runOne(t, now));
            }
        } catch (Exception e) {
            log.warn("AiCronScheduler 扫描异常", e);
        }
    }

    private boolean isDue(AiCronTask t, LocalDateTime now) {
        try {
            CronExpression expr = CronExpression.parse(t.getCronExpr());
            LocalDateTime baseline = t.getLastRunAt() != null ? t.getLastRunAt() : startupAt;
            LocalDateTime next = expr.next(baseline);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            log.warn("cron 解析失败 id={} expr={}", t.getId(), t.getCronExpr(), e);
            return false;
        }
    }

    private void runOne(AiCronTask task, LocalDateTime triggerAt) {
        log.info("AiCron 触发 id={} platform={} target={}", task.getId(), task.getPlatform(),
                StringUtils.defaultIfBlank(task.getTargetGroupId(), task.getTargetUserId()));
        try {
            BbSendMessage envelope = buildEnvelope(task);
            if (envelope == null) {
                updateStatus(task, triggerAt, "skipped");
                return;
            }
            MessageStreamSession session = bbMessageApi.startStream(envelope);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(task.getPrompt()));

            String sessionId = "cron-" + task.getId() + "-" + System.currentTimeMillis();
            String ownerId = task.getOwnerUserId();
            String platform = task.getPlatform();

            toolLoopExecutor.run(
                    messages,
                    toolRegistry.toToolDefinitions(),
                    (name, args) -> toolExecutor.invoke(name, args, ownerId, platform, sessionId),
                    maxSteps,
                    new StreamHandler() {
                        @Override public void onTextDelta(String delta) { session.appendDelta(delta); }
                        @Override public void onToolCalls(List<ToolCall> calls) {}
                        @Override public void onComplete(String fullText, String finishReason) {
                            session.complete();
                            updateStatus(task, triggerAt, "ok");
                        }
                        @Override public void onError(Throwable err) {
                            session.fail(err);
                            updateStatus(task, triggerAt, "error");
                        }
                    }
            );
        } catch (Exception e) {
            log.warn("AiCron 执行异常 id={}", task.getId(), e);
            updateStatus(task, triggerAt, "error");
        }
    }

    /** 根据 task 的平台 + bot_name 拿到对应 config 并构建消息信封。失败返回 null。 */
    private BbSendMessage buildEnvelope(AiCronTask t) {
        BbSendMessage env = new BbSendMessage();
        env.setBotType(t.getPlatform());
        env.setMessageType(StringUtils.isNoneBlank(t.getTargetGroupId()) ? MessageType.GROUP : MessageType.PRIVATE);
        env.setGroupId(t.getTargetGroupId());
        env.setUserId(t.getTargetUserId());

        Object config = resolveConfig(t.getPlatform(), t.getBotName());
        if (config == null) {
            log.warn("AiCron 找不到 {} 的 bot 配置 botName={}，任务 id={} 跳过", t.getPlatform(), t.getBotName(), t.getId());
            return null;
        }
        env.setConfig(config);
        // 对 OneBot / BB / QQ-OneBot 的 ws 引用，MVP 阶段不获取（cron 接 stateless 平台为主）
        return env;
    }

    private Object resolveConfig(String platform, String botName) {
        if (BotType.TELEGRAM.equals(platform)) {
            return pickFirstOrNamed(botConfig.getTelegram(), botName);
        } else if (BotType.DISCORD.equals(platform)) {
            return pickFirstOrNamed(botConfig.getDiscord(), botName);
        } else if (BotType.QQ.equals(platform)) {
            return pickFirstOrNamed(botConfig.getQq(), botName);
        } else if (BotType.ONEBOT.equals(platform)) {
            return pickFirstOrNamed(botConfig.getOnebot(), botName);
        } else if (BotType.BB.equals(platform)) {
            return pickFirstOrNamed(botConfig.getBb(), botName);
        }
        return null;
    }

    private <T> T pickFirstOrNamed(java.util.Map<String, T> map, String name) {
        if (map == null || map.isEmpty()) return null;
        if (StringUtils.isNoneBlank(name) && map.containsKey(name)) return map.get(name);
        return map.values().iterator().next();
    }

    private void updateStatus(AiCronTask task, LocalDateTime triggerAt, String status) {
        task.setLastRunAt(triggerAt);
        task.setLastStatus(status);
        cronTaskService.updateById(task);
    }
}
