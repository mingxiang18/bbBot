package com.bb.bot.handler.aiAgent;

import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.aiAgent.skills.SkillManifest;
import com.bb.bot.aiAgent.skills.SkillRegistry;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;

/**
 * Owner 专用：AI Agent SKILL 管理。
 *
 * <p>SKILL 全部托管于 {@code ai_skill} 表。增删改直接操作该表，
 * 改完用 reload 命令即时生效，无需重启。</p>
 *
 * <ul>
 *   <li>{@code /aiAgent.skill.list} —— 列出已注册 SKILL</li>
 *   <li>{@code /aiAgent.skill.reload} —— 从 ai_skill 表重新加载</li>
 * </ul>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI SKILL 管理")
public class BbAiSkillHandler {

    @Autowired
    private BbReplies replies;

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private SkillRegistry skillRegistry;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.skill.list", "aiAgent.skill.list"}, name = "查询SKILL")
    public void list(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Collection<SkillManifest> all = skillRegistry.all();
        if (all.isEmpty()) {
            replies.text(msg, "当前未注册任何 SKILL");
            return;
        }
        StringBuilder sb = new StringBuilder("已注册 SKILL ").append(all.size()).append(" 个：\n");
        for (SkillManifest s : all) {
            sb.append("- ").append(s.getName())
                    .append(": ").append(s.getDescription())
                    .append("\n");
        }
        replies.text(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.skill.reload", "aiAgent.skill.reload"}, name = "重载SKILL")
    public void reload(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        try {
            List<String> names = skillRegistry.reload();
            replies.text(msg, "SKILL 从 ai_skill 表重载完成，共 " + names.size() + " 个：" + names);
        } catch (Exception e) {
            log.warn("SKILL 重载失败", e);
            replies.text(msg, "SKILL 重载失败：" + e.getMessage());
        }
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        replies.text(msg, "无权限（仅 owner 可执行）");
        return true;
    }
}
