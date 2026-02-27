package com.bb.bot.common.util.qq;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.config.QqConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.entity.qq.QqChannelMessage;
import com.bb.bot.entity.qq.QqCommonPayloadEntity;
import com.bb.bot.entity.qq.QqGroupMessage;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * qq消息转换工具
 */
public class QQMessageUtil {

    /**
     * 用于@的cq码正则
     */
    public final static String AT_COMPILE_REG = "<@.*?>\\s?";

    /**
     * 将qq官方机器人群组消息转换为bb消息格式
     */
    public static BbReceiveMessage formatBbReceiveMessageFromGroup(QqCommonPayloadEntity message, QqConfig qqConfig) {
        //将qq消息Json转为实体
        QqGroupMessage qqGroupMessage = JSON.parseObject(JSON.toJSONString(message.getD()), QqGroupMessage.class);
        //封装为bb协议的消息实体
        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        bbReceiveMessage.setBotType(BotType.QQ);
        bbReceiveMessage.setMessageType(MessageType.GROUP);
        bbReceiveMessage.setUserId(qqGroupMessage.getAuthor().getMemberOpenId());
        bbReceiveMessage.setSender(new MessageUser(qqGroupMessage.getAuthor().getMemberOpenId(), qqGroupMessage.getAuthor().getMemberOpenId()));
        bbReceiveMessage.setGroupId(qqGroupMessage.getGroupOpenId());
        bbReceiveMessage.setMessageId(qqGroupMessage.getId());
        bbReceiveMessage.setMessage(qqGroupMessage.getContent());
        bbReceiveMessage.setMessageContentList(Collections.singletonList(BbMessageContent.buildTextContent(qqGroupMessage.getContent())));
        bbReceiveMessage.setConfig(qqConfig);

        MessageUser messageUser = new MessageUser();
        messageUser.setUserId("bb");
        messageUser.setNickname("bb");
        messageUser.setBotFlag(true);
        bbReceiveMessage.setAtUserList(Collections.singletonList(messageUser));
        return bbReceiveMessage;
    }

    /**
     * 将qq官方机器人频道消息转换为bb消息格式
     */
    public static BbReceiveMessage formatBbReceiveMessageFromChannel(QqCommonPayloadEntity message, QqConfig qqConfig) {
        //将qq消息Json转为实体
        QqChannelMessage qqChannelMessage = JSON.parseObject(JSON.toJSONString(message.getD()), QqChannelMessage.class);
        //封装为bb协议的消息实体
        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        bbReceiveMessage.setBotType(BotType.QQ);
        bbReceiveMessage.setMessageType(MessageType.CHANNEL);
        bbReceiveMessage.setUserId(qqChannelMessage.getAuthor().getId());
        bbReceiveMessage.setSender(new MessageUser(qqChannelMessage.getAuthor().getId(), qqChannelMessage.getAuthor().getUsername()));
        bbReceiveMessage.setGroupId(qqChannelMessage.getChannelId());
        bbReceiveMessage.setMessageId(qqChannelMessage.getId());
        bbReceiveMessage.setConfig(qqConfig);

        //设置消息内容，去掉@的cq码
        bbReceiveMessage.setMessage(qqChannelMessage.getContent().replaceAll(AT_COMPILE_REG, ""));
        bbReceiveMessage.setMessageContentList(Collections.singletonList(BbMessageContent.buildTextContent(qqChannelMessage.getContent())));

        if (qqChannelMessage.getMentions() != null && !qqChannelMessage.getMentions().isEmpty()) {
            //封装at用户对象列表
            bbReceiveMessage.setAtUserList(qqChannelMessage.getMentions().stream().map(qqUser -> {
                MessageUser messageUser = new MessageUser();
                messageUser.setUserId(qqUser.getId());
                messageUser.setNickname(qqUser.getUsername());
                if (qqUser.getBot()) {
                    messageUser.setBotFlag(true);
                }
                return messageUser;
            }).collect(Collectors.toList()));
        }
        return bbReceiveMessage;
    }
}
