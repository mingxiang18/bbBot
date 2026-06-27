package com.bb.bot.api;

import com.bb.bot.api.bb.BbToBbMessageApi;
import com.bb.bot.api.discord.DiscordMessageApi;
import com.bb.bot.api.oneBot.OneBotMessageApi;
import com.bb.bot.api.qq.QqToBbMessageApi;
import com.bb.bot.api.telegram.TelegramMessageApi;
import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 发起动作请求Api
 * @author ren
 */
@Slf4j
@Component
public class BbMessageApiImpl implements BbMessageApi{

    @Autowired
    private OneBotMessageApi oneBotMessageApi;

    @Autowired
    private QqToBbMessageApi qqToBbMessageApi;

    @Autowired
    private BbToBbMessageApi bbToBbMessageApi;

    @Autowired
    private TelegramMessageApi telegramMessageApi;

    @Autowired
    private DiscordMessageApi discordMessageApi;

    @Autowired(required = false)
    private MemoryEventRecorder memoryEventRecorder;

    /**
     * 发送消息
     */
    public void sendMessage(BbSendMessage bbSendMessage) {
        boolean sent = false;
        //安装机器人类型调用不同的api
        if (BotType.QQ.equals(bbSendMessage.getBotType())) {
            qqToBbMessageApi.sendMessage(bbSendMessage);
            sent = true;
        }else if (BotType.ONEBOT.equals(bbSendMessage.getBotType())) {
            oneBotMessageApi.sendMessage(bbSendMessage);
            sent = true;
        }else if (BotType.BB.equals(bbSendMessage.getBotType())) {
            bbToBbMessageApi.sendMessage(bbSendMessage);
            sent = true;
        }else if (BotType.TELEGRAM.equals(bbSendMessage.getBotType())) {
            telegramMessageApi.sendMessage(bbSendMessage);
            sent = true;
        }else if (BotType.DISCORD.equals(bbSendMessage.getBotType())) {
            discordMessageApi.sendMessage(bbSendMessage);
            sent = true;
        }else {
            log.warn("未知平台类型，消息未发送 botType={} group={} user={}",
                    bbSendMessage.getBotType(), bbSendMessage.getGroupId(), bbSendMessage.getUserId());
        }
        if (sent && memoryEventRecorder != null) {
            memoryEventRecorder.recordOutbound(bbSendMessage, "handler_reply");
        }
    };

    @Override
    public MessageStreamSession startStream(BbSendMessage bbSendMessage) {
        if (BotType.QQ.equals(bbSendMessage.getBotType())) {
            return qqToBbMessageApi.startStream(bbSendMessage);
        } else if (BotType.ONEBOT.equals(bbSendMessage.getBotType())) {
            return oneBotMessageApi.startStream(bbSendMessage);
        } else if (BotType.BB.equals(bbSendMessage.getBotType())) {
            return bbToBbMessageApi.startStream(bbSendMessage);
        } else if (BotType.TELEGRAM.equals(bbSendMessage.getBotType())) {
            return telegramMessageApi.startStream(bbSendMessage);
        } else if (BotType.DISCORD.equals(bbSendMessage.getBotType())) {
            return discordMessageApi.startStream(bbSendMessage);
        }
        // 未知平台：fallback 到 sendMessage
        return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
    }
}
