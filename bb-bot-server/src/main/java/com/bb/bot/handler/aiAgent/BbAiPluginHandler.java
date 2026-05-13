package com.bb.bot.handler.aiAgent;

import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.aiAgent.plugin.PluginLoader;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

/**
 * Owner 专用：AI Agent 插件管理。
 *
 * <ul>
 *   <li>{@code /aiAgent.plugin.list}</li>
 *   <li>{@code /aiAgent.plugin.reload} —— 全量重扫 plugins 目录</li>
 * </ul>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI 插件管理")
public class BbAiPluginHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private PluginLoader pluginLoader;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.plugin.list", "aiAgent.plugin.list"}, name = "查询插件")
    public void list(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        List<PluginLoader.LoadedPlugin> plugins = pluginLoader.listLoaded();
        if (plugins.isEmpty()) {
            reply(msg, "当前未加载任何插件");
            return;
        }
        StringBuilder sb = new StringBuilder("已加载插件 ").append(plugins.size()).append(" 个：\n");
        for (PluginLoader.LoadedPlugin p : plugins) {
            sb.append("- ").append(p.manifest.getName())
                    .append(" v").append(p.manifest.getVersion())
                    .append(" / ").append(p.instances.size()).append(" 个类")
                    .append("\n");
        }
        reply(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.plugin.reload", "aiAgent.plugin.reload"}, name = "重载插件")
    public void reload(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        try {
            List<String> names = pluginLoader.reloadAll();
            reply(msg, "插件重载完成：" + names);
        } catch (Exception e) {
            log.warn("插件重载失败", e);
            reply(msg, "插件重载失败：" + e.getMessage());
        }
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        reply(msg, "无权限（仅 owner 可执行）");
        return true;
    }

    private void reply(BbReceiveMessage msg, String text) {
        BbSendMessage send = new BbSendMessage(msg);
        send.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(send);
    }
}
