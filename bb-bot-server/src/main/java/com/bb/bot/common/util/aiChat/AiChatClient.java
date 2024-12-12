package com.bb.bot.common.util.aiChat;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * ai聊天工具类
 * @author ren
 */
@Slf4j
@Component
public class AiChatClient {

    @Autowired
    private RestUtils restUtils;

    /**
     * chatGPT的Url
     */
    @Value("${chatGPT.url:https://api.openai.com/v1/chat/completions}")
    private String chatGPTUrl;

    /**
     * chatGPT的apiKey
     */
    @Value("${chatGPT.apiKey:}")
    private String chatGPTApiKey;

    /**
     * 模型
     */
    @Value("${chatGPT.model:gpt-4}")
    private String model;

    /**
     * 出现错误时重试次数
     */
    @Value("${chatGPT.retryNum:10}")
    private Integer retryNum;

    /**
     * 是否配置了ai
     */
    public Boolean hasConfigAI() {
        return StringUtils.isNoneBlank(chatGPTApiKey);
    }

    /**
     * 询问chatGPT
     * @Param personality 机器人设定/性格
     * @Param question 问题
     * @Param chatHistoryList 聊天历史
     * @author ren
     */
    public String askChatGPT(String personality, String question, List<ChatHistory> chatHistoryList) {
        //如果apiKey为空，不执行
        if (StringUtils.isBlank(chatGPTApiKey)) {
            return null;
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("Authorization", "Bearer " + chatGPTApiKey);

        List<ChatGPTContent> chatGPTContentList = new ArrayList<>();

        //构建机器人性格消息体
        if (StringUtils.isNoneBlank(personality)) {
            chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.SYSTEM_ROLE, personality));
        }

        //如果聊天历史记录不为空，将历史记录构建成消息体
        if (!CollectionUtils.isEmpty(chatHistoryList)) {
            //过滤掉重复消息
            chatHistoryList = chatHistoryList.stream().filter(chatHistory -> !question.contains(chatHistory.getText())).toList();
            for (ChatHistory chatHistory : chatHistoryList) {
                //如果是机器人的qq号发送的消息，构建机器人消息体
                if ("bot".equals(chatHistory.getUserQq())) {
                    chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.ASSISTANT_ROLE, chatHistory.getText()));
                }else {
                    //如果是用户的qq号发送的消息，构建用户消息体
                    chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.USER_ROLE,
                            (StringUtils.isBlank(chatHistory.getUserName()) ? chatHistory.getUserQq() : chatHistory.getUserName()) + "：" + chatHistory.getText()
                    ));
                }
            }
        }

        //构建提问消息
        chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.USER_ROLE, question));

        int nowRetryNum = retryNum;
        while (nowRetryNum > 0) {
            try {
                //发送请求
                JSONObject chatGPTResponse = restUtils.post(chatGPTUrl, httpHeaders, new ChatGPTRequest(model, chatGPTContentList), JSONObject.class);
                //返回chatGPT回复
                return chatGPTResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } catch (Exception e) {
                log.error("chatGPT请求失败，剩余重试次数：" + nowRetryNum, e);
            } finally {
                nowRetryNum--;
            }
        }
        return "";
    }
}
