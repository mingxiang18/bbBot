package com.bb.bot.controller;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.common.util.telegram.TelegramMessageUtil;
import com.bb.bot.config.BotConfig;
import com.bb.bot.config.TelegramConfig;
import com.bb.bot.connection.telegram.TelegramApiCaller;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.telegram.TelegramUpdate;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * telegram回调控制器
 */
@Slf4j
@RestController
@Api(tags = "telegram回调Controller")
@RequestMapping("/telegram")
public class TelegramMessageController {

    @Resource
    private BotConfig botConfig;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TelegramApiCaller telegramApiCaller;

    /**
     * telegram webhook回调
     */
    @PostMapping(value = "/{botName}/webhook")
    @ApiOperation(value="telegram webhook回调")
    public Object webhook(@PathVariable("botName") String botName,
                          @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
                          @RequestBody byte[] body) {
        log.info("收到telegram webhook推送，botName：" + botName + "，报文：" + new String(body));
        TelegramConfig telegramConfig = getTelegramConfig(botName);
        checkSecretToken(telegramConfig, secretToken);

        TelegramUpdate telegramUpdate = JSON.parseObject(body, TelegramUpdate.class);
        if (telegramUpdate == null || (telegramUpdate.getMessage() == null && telegramUpdate.getChannelPost() == null)) {
            return "ok";
        }

        BbReceiveMessage bbReceiveMessage = TelegramMessageUtil.formatBbReceiveMessage(telegramUpdate, telegramConfig, telegramApiCaller);
        publisher.publishEvent(bbReceiveMessage);
        return "ok";
    }

    private TelegramConfig getTelegramConfig(String botName) {
        TelegramConfig telegramConfig = botConfig.getTelegram().get(botName);
        if (telegramConfig == null || !telegramConfig.isEnable()) {
            throw new RuntimeException("telegram bot not found: " + botName);
        }
        return telegramConfig;
    }

    private void checkSecretToken(TelegramConfig telegramConfig, String secretToken) {
        if (StringUtils.isBlank(telegramConfig.getSecretToken())) {
            return;
        }
        if (!telegramConfig.getSecretToken().equals(secretToken)) {
            throw new RuntimeException("telegram webhook secret token verify failed");
        }
    }
}
