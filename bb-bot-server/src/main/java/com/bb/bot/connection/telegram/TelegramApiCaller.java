package com.bb.bot.connection.telegram;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.config.TelegramConfig;
import com.bb.bot.entity.telegram.TelegramFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * telegram bot api调用器
 */
@Slf4j
@Component
public class TelegramApiCaller {

    @Autowired
    private RestUtils restUtils;

    public void sendMessage(TelegramConfig telegramConfig, String chatId, String text, String replyMessageId) {
        sendMessageReturningId(telegramConfig, chatId, text, replyMessageId);
    }

    /**
     * 发送消息并返回 Telegram 分配的 message_id（流式 edit 用）。失败时返回 null。
     */
    public String sendMessageReturningId(TelegramConfig telegramConfig, String chatId, String text, String replyMessageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("chat_id", chatId);
        params.put("text", text);
        putReplyParameters(params, replyMessageId);

        JSONObject response = restUtils.postForForm(buildApiUrl(telegramConfig, "sendMessage"), params, JSONObject.class);
        checkResponse(response);
        JSONObject result = response.getJSONObject("result");
        return result == null ? null : result.getString("message_id");
    }

    /**
     * 编辑已发送消息文本（流式回复用）。
     *
     * <p>Telegram 限制：同一条消息 1 秒内只能编辑一次，全 chat 维度也有节流。
     * 若 edit 失败（比如内容未变化），日志告警但不抛异常 —— 流式吐字过程中不应中断。</p>
     */
    public void editMessageText(TelegramConfig telegramConfig, String chatId, String messageId, String text) {
        Map<String, Object> params = new HashMap<>();
        params.put("chat_id", chatId);
        params.put("message_id", messageId);
        params.put("text", text);
        try {
            JSONObject response = restUtils.postForForm(buildApiUrl(telegramConfig, "editMessageText"), params, JSONObject.class);
            if (response == null || !Boolean.TRUE.equals(response.getBoolean("ok"))) {
                log.warn("telegram editMessageText 失败（流式吐字非致命）: {}", response);
            }
        } catch (Exception e) {
            log.warn("telegram editMessageText 异常（流式吐字非致命）", e);
        }
    }

    public void sendPhoto(TelegramConfig telegramConfig, String chatId, File photo, String caption, String replyMessageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("chat_id", chatId);
        params.put("photo", new FileSystemResource(photo));
        if (StringUtils.isNoneBlank(caption)) {
            params.put("caption", caption);
        }
        putReplyParameters(params, replyMessageId);

        checkResponse(restUtils.postForForm(buildApiUrl(telegramConfig, "sendPhoto"), params, JSONObject.class));
    }

    public void sendPhoto(TelegramConfig telegramConfig, String chatId, String photoUrl, String caption, String replyMessageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("chat_id", chatId);
        params.put("photo", photoUrl);
        if (StringUtils.isNoneBlank(caption)) {
            params.put("caption", caption);
        }
        putReplyParameters(params, replyMessageId);

        checkResponse(restUtils.postForForm(buildApiUrl(telegramConfig, "sendPhoto"), params, JSONObject.class));
    }

    /**
     * 以文档附件形式发送本地文件（Telegram Bot API sendDocument，multipart 上传 document 字段）。
     * 任意类型文件都走这里（非图片），最大 50MB（Bot API 限制）。
     */
    public void sendDocument(TelegramConfig telegramConfig, String chatId, File document, String caption, String replyMessageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("chat_id", chatId);
        params.put("document", new FileSystemResource(document));
        if (StringUtils.isNoneBlank(caption)) {
            params.put("caption", caption);
        }
        putReplyParameters(params, replyMessageId);

        checkResponse(restUtils.postForForm(buildApiUrl(telegramConfig, "sendDocument"), params, JSONObject.class));
    }

    public TelegramFile getFile(TelegramConfig telegramConfig, String fileId) {
        Map<String, Object> params = new HashMap<>();
        params.put("file_id", fileId);
        JSONObject response = restUtils.postForForm(buildApiUrl(telegramConfig, "getFile"), params, JSONObject.class);
        checkResponse(response);
        return JSON.parseObject(JSON.toJSONString(response.get("result")), TelegramFile.class);
    }

    public String buildFileUrl(TelegramConfig telegramConfig, String filePath) {
        return getBaseUrl(telegramConfig) + "/file/bot" + telegramConfig.getToken() + "/" + filePath;
    }

    private String buildApiUrl(TelegramConfig telegramConfig, String method) {
        return getBaseUrl(telegramConfig) + "/bot" + telegramConfig.getToken() + "/" + method;
    }

    private String getBaseUrl(TelegramConfig telegramConfig) {
        return StringUtils.defaultIfBlank(telegramConfig.getBaseUrl(), "https://api.telegram.org");
    }

    private void putReplyParameters(Map<String, Object> params, String replyMessageId) {
        if (StringUtils.isNoneBlank(replyMessageId)) {
            Map<String, Object> replyParameters = new HashMap<>();
            replyParameters.put("message_id", replyMessageId);
            params.put("reply_parameters", JSON.toJSONString(replyParameters));
        }
    }

    private void checkResponse(JSONObject response) {
        if (response == null || !Boolean.TRUE.equals(response.getBoolean("ok"))) {
            log.error("telegram api调用失败: {}", response);
            throw new RuntimeException("telegram api call failed: " + response);
        }
    }
}
