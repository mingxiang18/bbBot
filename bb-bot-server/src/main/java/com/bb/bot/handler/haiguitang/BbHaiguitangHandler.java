package com.bb.bot.handler.haiguitang;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.prompt.PromptProperties;
import com.bb.bot.common.util.aiChat.prompt.PromptRenderer;
import com.bb.bot.common.util.aiChat.provider.AIException;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.qq.QQMessageUtil;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 海龟汤事件处理器。AI 调用走 {@link AiChatService}，prompt 走 {@link PromptProperties}。
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "海龟汤")
public class BbHaiguitangHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private PromptProperties promptProperties;

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"海龟汤", "/海龟汤"}, name = "海龟汤")
    public void haiguitangStartHandle(BbReceiveMessage bbReceiveMessage) {
        UserConfigValue haiguitangConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, "system")
                .eq(UserConfigValue::getType, "haiguitang")
                .last("ORDER BY RAND() LIMIT 1"));
        if (haiguitangConfig == null) {
            sendText(bbReceiveMessage, "不存在海龟汤问题");
            return;
        }

        String[] haiguitang = haiguitangConfig.getValueName().split("%n%");
        String question = haiguitang[0];

        if (userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, bbReceiveMessage.getGroupId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION)) == null) {
            UserConfigValue occupation = new UserConfigValue();
            occupation.setGroupId(bbReceiveMessage.getGroupId());
            occupation.setType(RuleType.OCCUPATION);
            occupation.setKeyName(this.getClass().getDeclaredMethod("haiguitangJudgeHandle", BbReceiveMessage.class).getName());
            occupation.setValueName(haiguitangConfig.getId().toString());
            userConfigValueService.save(occupation);
        } else {
            sendText(bbReceiveMessage, "当前正在进行游戏中，无法再次开始海龟汤噢");
        }

        sendText(bbReceiveMessage, "汤面如下：" + question + "\n发送【海龟汤答案】可以结束本次游戏噢。");
    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.OCCUPATION, name = "海龟汤猜谜")
    public void haiguitangJudgeHandle(BbReceiveMessage bbReceiveMessage) {
        UserConfigValue groupConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, bbReceiveMessage.getGroupId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION)
                .eq(UserConfigValue::getKeyName, this.getClass().getDeclaredMethod("haiguitangJudgeHandle", BbReceiveMessage.class).getName()));
        if (groupConfig == null) {
            removeOccupation(bbReceiveMessage);
            sendText(bbReceiveMessage, "当前没有海龟汤问题");
            return;
        }

        UserConfigValue haiguitangConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getId, Long.valueOf(groupConfig.getValueName())));
        if (haiguitangConfig == null) {
            removeOccupation(bbReceiveMessage);
            sendText(bbReceiveMessage, "不存在海龟汤问题");
            return;
        }

        String[] haiguitang = haiguitangConfig.getValueName().split("%n%");
        String question = haiguitang[0];
        String answer = haiguitang[1];

        if (bbReceiveMessage.getMessage().contains("海龟汤答案")) {
            removeOccupation(bbReceiveMessage);
            sendText(bbReceiveMessage, "汤底如下噢：" + answer);
            return;
        }

        String fullPersonality = PromptRenderer.render(
                promptProperties.getHaiguitang().getJudge(),
                Map.of("question", question, "answer", answer));
        List<ChatMessage> messages = List.of(
                ChatMessage.system(fullPersonality),
                ChatMessage.user(bbReceiveMessage.getMessage()));

        String botAnswer;
        try {
            botAnswer = aiChatService.chat(messages);
        } catch (AIException e) {
            log.error("haiguitang judge call failed (type={})", e.getErrorType(), e);
            sendText(bbReceiveMessage, "裁判暂时不在线，稍后再试一下噢");
            return;
        }
        if (botAnswer == null) {
            sendText(bbReceiveMessage, "海龟汤裁判尚未配置 AI，无法判断");
            return;
        }
        sendText(bbReceiveMessage, botAnswer);
    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"生成海龟汤", "/生成海龟汤"}, name = "生成海龟汤")
    public void haiguitangGenerateHandle(BbReceiveMessage bbReceiveMessage) {
        String personality = promptProperties.getHaiguitang().getGenerate();
        List<ChatMessage> messages = List.of(
                ChatMessage.system(personality),
                ChatMessage.user("你是一个海龟汤出题者，请帮我出一道海龟汤题目。"));

        String answer;
        try {
            answer = aiChatService.chat(messages);
        } catch (AIException e) {
            log.error("haiguitang generate call failed", e);
            sendText(bbReceiveMessage, "AI 暂时不在线，稍后再试一下噢");
            return;
        }
        if (answer == null) {
            sendText(bbReceiveMessage, "尚未配置 AI，无法生成");
            return;
        }
        sendText(bbReceiveMessage, answer);
    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^设置海龟汤", "^/设置海龟汤"}, name = "设置海龟汤")
    public void setHaiguitangHandle(BbReceiveMessage bbReceiveMessage) {
        String haiguitangContent = bbReceiveMessage.getMessage()
                .replaceAll(QQMessageUtil.AT_COMPILE_REG, "")
                .replace("设置海龟汤", "")
                .replace("/设置海龟汤", "");

        if (!haiguitangContent.contains("%n%")) {
            sendText(bbReceiveMessage, "海龟汤格式不符合要求");
            return;
        }

        UserConfigValue haiguitangConfig = new UserConfigValue();
        haiguitangConfig.setUserId("system");
        haiguitangConfig.setType("haiguitang");
        haiguitangConfig.setKeyName("1");
        haiguitangConfig.setValueName(haiguitangContent);
        userConfigValueService.save(haiguitangConfig);

        sendText(bbReceiveMessage, "设置完成");
    }

    private void removeOccupation(BbReceiveMessage source) {
        userConfigValueService.remove(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, source.getGroupId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION));
    }

    private void sendText(BbReceiveMessage source, String text) {
        BbSendMessage out = new BbSendMessage(source);
        out.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(out);
    }
}
