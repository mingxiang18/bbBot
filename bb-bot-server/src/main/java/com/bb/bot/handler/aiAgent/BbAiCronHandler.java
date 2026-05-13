package com.bb.bot.handler.aiAgent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.aiAgent.entity.AiCronTask;
import com.bb.bot.database.aiAgent.service.IAiCronTaskService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owner 专用：AI Agent 定时任务管理命令。
 *
 * <ul>
 *   <li>{@code /aiAgent.cron.add "<cron>" <prompt>} —— 添加定时任务（目标当前频道）</li>
 *   <li>{@code /aiAgent.cron.list}</li>
 *   <li>{@code /aiAgent.cron.remove <id>}</li>
 *   <li>{@code /aiAgent.cron.toggle <id>} —— 启用 / 停用</li>
 * </ul>
 *
 * <p>cron 表达式用 Spring CronExpression 格式：6 字段（秒 分 时 日 月 周）。</p>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI Cron 管理")
public class BbAiCronHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private IAiCronTaskService cronTaskService;

    // 匹配 "<cron 表达式带空格>" 后接 prompt
    private static final Pattern ADD_RE = Pattern.compile("^/?aiAgent\\.cron\\.add\\s+\"([^\"]+)\"\\s+(.+)$",
            Pattern.DOTALL);
    private static final Pattern REMOVE_RE = Pattern.compile("^/?aiAgent\\.cron\\.remove\\s+(\\d+)\\s*$");
    private static final Pattern TOGGLE_RE = Pattern.compile("^/?aiAgent\\.cron\\.toggle\\s+(\\d+)\\s*$");

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.cron\\.add\\s"}, name = "新增定时任务")
    public void add(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = ADD_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /aiAgent.cron.add \"<cron 6 字段>\" <prompt>");
            return;
        }
        String cron = m.group(1);
        String prompt = m.group(2);
        try {
            CronExpression.parse(cron);
        } catch (Exception e) {
            reply(msg, "cron 表达式无效：" + e.getMessage());
            return;
        }
        AiCronTask task = new AiCronTask();
        task.setOwnerUserId(msg.getUserId());
        task.setPlatform(msg.getBotType());
        task.setTargetGroupId(msg.getGroupId());
        task.setTargetUserId(StringUtils.isBlank(msg.getGroupId()) ? msg.getUserId() : null);
        task.setCronExpr(cron);
        task.setPrompt(prompt);
        task.setEnabled(true);
        task.setCreatedAt(LocalDateTime.now());
        cronTaskService.save(task);
        reply(msg, "已添加定时任务 id=" + task.getId() + " cron=" + cron);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.cron.list", "aiAgent.cron.list"}, name = "查询定时任务")
    public void list(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        List<AiCronTask> rows = cronTaskService.list(new LambdaQueryWrapper<AiCronTask>()
                .eq(AiCronTask::getOwnerUserId, msg.getUserId())
                .orderByDesc(AiCronTask::getId)
                .last("limit 20"));
        if (rows.isEmpty()) {
            reply(msg, "你没有定时任务");
            return;
        }
        StringBuilder sb = new StringBuilder("你的定时任务（最多 20 条）：\n");
        for (AiCronTask t : rows) {
            sb.append("[").append(t.getId()).append("] ")
                    .append(Boolean.TRUE.equals(t.getEnabled()) ? "[on]" : "[off]")
                    .append(" ").append(t.getCronExpr())
                    .append(" → ").append(abbrev(t.getPrompt(), 40))
                    .append("\n");
        }
        reply(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.cron\\.remove\\s"}, name = "删除定时任务")
    public void remove(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = REMOVE_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /aiAgent.cron.remove <id>");
            return;
        }
        long id = Long.parseLong(m.group(1));
        boolean removed = cronTaskService.remove(new LambdaQueryWrapper<AiCronTask>()
                .eq(AiCronTask::getId, id)
                .eq(AiCronTask::getOwnerUserId, msg.getUserId()));
        reply(msg, removed ? ("已删除 id=" + id) : "id=" + id + " 不存在或非你创建");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.cron\\.toggle\\s"}, name = "切换定时任务启用状态")
    public void toggle(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = TOGGLE_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /aiAgent.cron.toggle <id>");
            return;
        }
        long id = Long.parseLong(m.group(1));
        AiCronTask t = cronTaskService.getById(id);
        if (t == null || !t.getOwnerUserId().equals(msg.getUserId())) {
            reply(msg, "id=" + id + " 不存在或非你创建");
            return;
        }
        t.setEnabled(!Boolean.TRUE.equals(t.getEnabled()));
        cronTaskService.updateById(t);
        reply(msg, "id=" + id + " 已 " + (Boolean.TRUE.equals(t.getEnabled()) ? "启用" : "停用"));
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        reply(msg, "无权限（仅 owner 可执行）");
        return true;
    }

    private String textOf(BbReceiveMessage msg) {
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : msg.getMessageContentList()) {
            if (c.getData() != null) sb.append(c.getData().toString()).append(" ");
        }
        return sb.toString();
    }

    private String abbrev(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private void reply(BbReceiveMessage msg, String text) {
        BbSendMessage send = new BbSendMessage(msg);
        send.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(send);
    }
}
