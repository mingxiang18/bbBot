package com.bb.bot.handler.aiChat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.service.IChatHistoryService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ai聊天事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, name = "AI聊天")
public class BbAiChatHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private IChatHistoryService chatHistoryService;

    /**
     * ai回复需要携带的历史记录数量
     */
    @Value("${aiChat.chatHistoryNum:10}")
    private int chatHistoryNum;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.DEFAULT, name = "ai自动回复")
    public void aiChatHandle(BbReceiveMessage bbReceiveMessage) {
        //查询历史记录
        List<ChatHistory> chatHistoryList = new ArrayList<>();
        if (MessageType.GROUP.equals(bbReceiveMessage.getMessageType())) {
            //群组
            chatHistoryList = chatHistoryService.list(new LambdaQueryWrapper<ChatHistory>()
                            .eq(ChatHistory::getGroupId, bbReceiveMessage.getGroupId())
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }else if (MessageType.PRIVATE.equals(bbReceiveMessage.getMessageType())) {
            chatHistoryList = chatHistoryService.list(new LambdaQueryWrapper<ChatHistory>()
                            .isNull(ChatHistory::getGroupId)
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }

        String answer = aiChatClient.askChatGPT(bbReceiveMessage.getMessage(), chatHistoryList);

        //保存机器人回复
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(IdWorker.getIdStr());
        chatHistory.setUserQq("bot");
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setText(answer);
        chatHistoryService.save(chatHistory);

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent(answer))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
