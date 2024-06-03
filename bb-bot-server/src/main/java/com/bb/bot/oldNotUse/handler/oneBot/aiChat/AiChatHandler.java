package com.bb.bot.oldNotUse.handler.oneBot.aiChat;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.oldNotUse.api.oneBot.ActionApi;
import com.bb.bot.config.BotConfig;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.MessageType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import com.bb.bot.oldNotUse.event.oneBot.ReceiveMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

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
    private BotConfig botConfig;

    @Autowired
    private AiChatClient aiChatClient;

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
        String content = aiChatClient.askChatGPT(message.getMessage(), chatHistoryList);
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
}
