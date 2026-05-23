package com.bb.bot.aiAgent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.aiAgent.entity.AiCronTask;
import com.bb.bot.database.aiAgent.service.IAiCronTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务的自然语言入口（owner 专用）。
 *
 * <p>取代旧的 {@code /aiAgent.cron.*} 命令：用户直接说「每天早上 9 点提醒我喝水」，
 * 模型自行调 {@code cron_add} 等工具完成增删查改。任务的目标频道由当前会话上下文
 * （{@link ToolCallContext}）推断，而非让模型去猜群号。</p>
 *
 * <p>任务由 {@code AiCronScheduler} 周期触发，到点后把 {@code prompt} 当成用户消息
 * 派给 cron Agent 执行，结果发回创建时所在的频道。</p>
 */
@Slf4j
@Component
public class CronTools {

    /** Spring CronExpression 格式说明 + 示例，给 LLM 看，确保它生成的表达式能被 parse。 */
    private static final String CRON_FORMAT_HINT =
            "Spring CronExpression 格式：6 个字段，用空格分隔，依次是「秒 分 时 日 月 周」"
                    + "（注意第一位是秒，不是标准 5 段 crontab，也不要写 Quartz 的 ? 和年份）。"
                    + "示例：每天 9:00 → `0 0 9 * * *`；每周一 9:30 → `0 30 9 * * MON`；"
                    + "每小时整点 → `0 0 * * * *`；每 5 分钟 → `0 0/5 * * * *`。时区为服务器本地时区。";

    @Autowired
    private IAiCronTaskService cronTaskService;

    // ─────────────────────────── 新增 ───────────────────────────
    @AiTool(
            name = "cron_add",
            description = "为用户创建一个定时任务：到点后由你（AI）执行 prompt 描述的事，结果发回当前频道。"
                    + "适合「每天/每周/每隔…提醒我…」「定时播报…」这类需求。"
                    + "cronExpr 必须是 " + CRON_FORMAT_HINT
                    + " prompt 写「到点时要做的事」（如『提醒主人喝水』『查今天天气并播报』）。返回任务 id。",
            requiresOwner = true)
    public Map<String, Object> add(
            @AiToolParam(name = "cronExpr", description = "Spring 6 段 cron 表达式，秒在最前，如 0 0 9 * * *")
            String cronExpr,
            @AiToolParam(name = "prompt", description = "到点时派给 AI 执行的任务内容（自然语言）")
            String prompt) {
        Map<String, Object> result = new LinkedHashMap<>();
        String userId = MemoryToolContext.getUserId();

        if (StringUtils.isBlank(cronExpr) || StringUtils.isBlank(prompt)) {
            result.put("error", "missing_arguments");
            result.put("hint", "cronExpr 和 prompt 都必填");
            return result;
        }
        try {
            CronExpression.parse(cronExpr);
        } catch (Exception e) {
            result.put("error", "invalid_cron_expr");
            result.put("message", e.getMessage());
            result.put("hint", CRON_FORMAT_HINT);
            return result;
        }

        // 目标频道从当前会话上下文推断：在群里建 → 发回该群；私聊建 → 发回该用户
        String platform = StringUtils.defaultIfBlank(ToolCallContext.getPlatform(), BotType.BB);
        String groupId = ToolCallContext.getGroupId();

        try {
            AiCronTask task = new AiCronTask();
            task.setOwnerUserId(userId);
            task.setPlatform(platform);
            task.setTargetGroupId(groupId);
            task.setTargetUserId(StringUtils.isBlank(groupId) ? userId : null);
            task.setCronExpr(cronExpr);
            task.setPrompt(prompt);
            task.setEnabled(true);
            task.setCreatedAt(LocalDateTime.now());
            cronTaskService.save(task);

            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("cronExpr", cronExpr);
            result.put("target", StringUtils.isNoneBlank(groupId) ? ("群 " + groupId) : ("私聊 " + userId));
            return result;
        } catch (Exception e) {
            log.warn("cron_add 失败 user={}", userId, e);
            result.put("error", "creation_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    // ─────────────────────────── 查询 ───────────────────────────
    @AiTool(
            name = "cron_list",
            description = "列出当前用户创建的所有定时任务（含 id、cron 表达式、要做的事、是否启用、上次执行状态）。"
                    + "用户问「我有哪些定时任务/提醒」时调用。",
            requiresOwner = true)
    public Map<String, Object> list() {
        Map<String, Object> result = new LinkedHashMap<>();
        String userId = MemoryToolContext.getUserId();
        try {
            List<AiCronTask> rows = cronTaskService.list(new LambdaQueryWrapper<AiCronTask>()
                    .eq(AiCronTask::getOwnerUserId, userId)
                    .orderByDesc(AiCronTask::getId));
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiCronTask t : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", t.getId());
                item.put("cronExpr", t.getCronExpr());
                item.put("prompt", t.getPrompt());
                item.put("enabled", Boolean.TRUE.equals(t.getEnabled()));
                item.put("lastRunAt", t.getLastRunAt());
                item.put("lastStatus", t.getLastStatus());
                items.add(item);
            }
            result.put("success", true);
            result.put("count", items.size());
            result.put("tasks", items);
            return result;
        } catch (Exception e) {
            log.warn("cron_list 失败 user={}", userId, e);
            result.put("error", "query_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    // ─────────────────────────── 删除 ───────────────────────────
    @AiTool(
            name = "cron_remove",
            description = "删除一个定时任务（按 id，先用 cron_list 查到 id）。只能删自己创建的任务。",
            requiresOwner = true)
    public Map<String, Object> remove(
            @AiToolParam(name = "taskId", description = "要删除的任务 id")
            Long taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String userId = MemoryToolContext.getUserId();
        if (taskId == null) {
            result.put("error", "missing_arguments");
            result.put("hint", "taskId 必填");
            return result;
        }
        try {
            AiCronTask t = cronTaskService.getById(taskId);
            if (t == null || !userId.equals(t.getOwnerUserId())) {
                result.put("error", "not_found");
                result.put("message", "id=" + taskId + " 不存在或非你创建");
                return result;
            }
            cronTaskService.removeById(taskId);
            result.put("success", true);
            result.put("taskId", taskId);
            return result;
        } catch (Exception e) {
            log.warn("cron_remove 失败 user={} taskId={}", userId, taskId, e);
            result.put("error", "deletion_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    // ─────────────────────────── 启用/停用 ───────────────────────────
    @AiTool(
            name = "cron_toggle",
            description = "启用或停用一个定时任务（按 id）。不传 enabled 则在当前状态间切换。只能操作自己创建的任务。",
            requiresOwner = true)
    public Map<String, Object> toggle(
            @AiToolParam(name = "taskId", description = "要操作的任务 id")
            Long taskId,
            @AiToolParam(name = "enabled", description = "true=启用，false=停用；不传则切换", required = false)
            Boolean enabled) {
        Map<String, Object> result = new LinkedHashMap<>();
        String userId = MemoryToolContext.getUserId();
        if (taskId == null) {
            result.put("error", "missing_arguments");
            result.put("hint", "taskId 必填");
            return result;
        }
        try {
            AiCronTask t = cronTaskService.getById(taskId);
            if (t == null || !userId.equals(t.getOwnerUserId())) {
                result.put("error", "not_found");
                result.put("message", "id=" + taskId + " 不存在或非你创建");
                return result;
            }
            boolean newState = enabled != null ? enabled : !Boolean.TRUE.equals(t.getEnabled());
            t.setEnabled(newState);
            cronTaskService.updateById(t);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("enabled", newState);
            return result;
        } catch (Exception e) {
            log.warn("cron_toggle 失败 user={} taskId={}", userId, taskId, e);
            result.put("error", "update_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
