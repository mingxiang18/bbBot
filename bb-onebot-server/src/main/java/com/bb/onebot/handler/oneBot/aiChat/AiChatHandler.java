package com.bb.onebot.handler.oneBot.aiChat;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.oneBot.ActionApi;
import com.bb.onebot.config.BotConfig;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.MessageType;
import com.bb.onebot.database.chatHistory.entity.ChatHistory;
import com.bb.onebot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.onebot.entity.oneBot.ReceiveMessage;
import com.bb.onebot.event.oneBot.ReceiveMessageEvent;
import com.bb.onebot.util.RestClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ai聊天处理者
 * @author ren
 */
@Slf4j
@BootEventHandler
public class AiChatHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private RestClient restClient;

    @Autowired
    private BotConfig botConfig;

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
     * chatGPT的性格
     */
    @Value("${chatGPT.personality:你的名字是冥想bb。你在一个群聊中，里面有零碎的各种消息，请尝试判断上下文，你的回复要像平时聊天一样。同时你是个鱿鱼偶像，请用最能表现偶像的活力和可爱的方式回复，回复时要加上颜文字。}")
    private String chatGPTPersonality;

    /**
     * ai随机回复数值
     */
    @Value("${aiChat.replyNum:0}")
    private double replyNum;

    /**
     * ai回复需要携带的历史记录数量
     */
    @Value("${aiChat.chatHistoryNum:10}")
    private int chatHistoryNum;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    /**
     * cq码正则
     */
    private String cqRegex = "\\[CQ:.*?\\]";

    /**
     * ai聊天
     * @author ren
     */
    @Rule(eventType = EventType.MESSAGE, name = "ai聊天")
    public void aiChatHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        //是否应该回复标识
        boolean replyFlag = false;

        //判断是否需要回复
        if (MessageType.PRIVATE.equals(message.getMessageType())) {
            //如果是私人消息，默认回复
            replyFlag = true;
        }else if (message.getMessage().contains(botConfig.getQq())){
            //如果消息中包含机器人qq号（被@），默认回复
            replyFlag = true;
        }else {
            //如果以上都不满足，跟据配置的replyNum判断，如果replyNum为0则不回复，否则随机一个0-1之间的数，回复数值大于replyNum则回复
            double randomDouble = RandomUtil.randomDouble(0, 1);
            log.info("ai回复抽取随机值：" + randomDouble);
            if (replyNum != 0d && randomDouble > replyNum) {
                replyFlag = true;
            }
        }

        //如果不需要回复，则结束
        if (!replyFlag) {
            return;
        }

        //查询历史记录
        List<ChatHistory> chatHistoryList = new ArrayList<>();
        if (MessageType.GROUP.equals(message.getMessageType())) {
            //群组
            chatHistoryList = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                    .eq(ChatHistory::getGroupId, groupId)
                    .orderByDesc(ChatHistory::getCreateTime)
                    .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }else {
            //私聊
            chatHistoryList = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                    .eq(ChatHistory::getUserQq, userId)
                    .isNull(ChatHistory::getGroupId)
                    .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }

        //调用chatGPT获取回复消息
        String content = askChatGPT(message.getMessage(), chatHistoryList);
        content = content.replaceAll(cqRegex, "");

        //将机器人回复内容也保存到数据库
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setUserQq(botConfig.getQq());
        chatHistory.setGroupId(message.getGroupId());
        chatHistory.setText(content);
        chatHistoryMapper.insert(chatHistory);

        //发送消息
        if (MessageType.GROUP.equals(message.getMessageType())) {
            actionApi.sendGroupMessage(groupId, content);
        }else {
            actionApi.sendPrivateMessage(userId, content);
        }
    }

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
                if (botConfig.getQq().equals(chatHistory.getUserQq())) {
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
        JSONObject chatGPTResponse = restClient.post(chatGPTUrl, httpHeaders, new ChatGPTRequest(chatGPTContentList), JSONObject.class);
        //返回chatGPT回复
        return chatGPTResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }
}
