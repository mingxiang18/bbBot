package com.bb.bot.oldNotUse.handler.qq.aiChat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.oldNotUse.api.qq.QqMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.service.IChatHistoryService;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.QqMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ai聊天事件处理器
 * @author ren
 */
//@BootEventHandler(botType = BotType.QQ)
public class QqAiChatHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

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
    public void aiChatHandle(QqMessage event) {
        //查询历史记录
        List<ChatHistory> chatHistoryList = new ArrayList<>();
        if (StringUtils.isNoneBlank(event.getChannelId())) {
            //群组
            chatHistoryList = chatHistoryService.list(new LambdaQueryWrapper<ChatHistory>()
                            .eq(ChatHistory::getGroupId, event.getChannelId())
                            .orderByDesc(ChatHistory::getCreateTime)
                            .last("limit " + chatHistoryNum))
                    .stream().sorted(Comparator.comparing(ChatHistory::getCreateTime)).collect(Collectors.toList());
        }

//        String answer = aiChatClient.askChatGPT(event.getContent(), chatHistoryList);
        String answer = "";

        //保存机器人回复
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(IdWorker.getIdStr());
        chatHistory.setUserQq("bot");
        chatHistory.setGroupId(event.getChannelId());
        chatHistory.setText(answer);
        chatHistoryService.save(chatHistory);

        //发送消息
        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent(answer);
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }
}
