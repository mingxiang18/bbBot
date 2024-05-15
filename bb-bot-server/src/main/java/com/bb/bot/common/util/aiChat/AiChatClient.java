package com.bb.bot.common.util.aiChat;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.util.RestUtils;
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
     * chatGPT的性格
     */
    @Value("${chatGPT.personality:你的名字是冥想bb。你在一个群聊中，里面有零碎的各种消息，请尝试判断上下文，你的回复要像平时聊天一样。同时你是个鱿鱼偶像，请用最能表现偶像的活力和可爱的方式回复，回复时要加上颜文字。}")
    private String chatGPTPersonality;

    /**
     * 询问chatGPT
     * @Param question 问题
     * @Param chatHistoryList 聊天历史
     * @author ren
     */
    public String askChatGPT(String question, List<ChatHistory> chatHistoryList) {
        //如果apiKey为空，不执行
        if (StringUtils.isBlank(chatGPTApiKey)) {
            return null;
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("Authorization", "Bearer " + chatGPTApiKey);

        List<ChatGPTContent> chatGPTContentList = new ArrayList<>();

        //构建机器人性格消息体
        chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.SYSTEM_ROLE, chatGPTPersonality));

        //如果聊天历史记录不为空，将历史记录构建成消息体
        if (!CollectionUtils.isEmpty(chatHistoryList)) {
            for (ChatHistory chatHistory : chatHistoryList) {
                //如果是机器人的qq号发送的消息，构建机器人消息体
                if ("bot".equals(chatHistory.getUserQq())) {
                    chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.ASSISTANT_ROLE, chatHistory.getText()));
                }else {
                    //如果是用户的qq号发送的消息，构建用户消息体
                    chatGPTContentList.add(new ChatGPTContent(chatHistory.getText()));
                }
            }
        }

        //构建提问消息
        chatGPTContentList.add(new ChatGPTContent(ChatGPTContent.USER_ROLE, question));

        //发送请求
        JSONObject chatGPTResponse = restUtils.post(chatGPTUrl, httpHeaders, new ChatGPTRequest(model, chatGPTContentList), JSONObject.class);
        //返回chatGPT回复
        return chatGPTResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }
}
