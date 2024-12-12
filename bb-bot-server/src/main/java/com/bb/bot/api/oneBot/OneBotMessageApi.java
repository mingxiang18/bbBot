package com.bb.bot.api.oneBot;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.oneBot.Action;
import com.bb.bot.entity.oneBot.Message;
import com.bb.bot.entity.oneBot.MessageContent;
import com.bb.bot.common.util.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class OneBotMessageApi {

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }
        //将bb协议消息封装成oneBot结构消息
        List<MessageContent> onebotMessageContentList = new ArrayList<>();
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.TEXT.equals(bbMessageContent.getType())) {
                //构建文本消息
                onebotMessageContentList.add(MessageContent.buildTextContent(bbMessageContent.getData().toString()));
            }else if (BbSendMessageType.AT.equals(bbMessageContent.getType()) && MessageType.GROUP.equals(bbSendMessage.getMessageType())) {
                //构建at消息
                onebotMessageContentList.add(MessageContent.buildAtMessageContent(bbMessageContent.getData().toString()));
                //at之后要加空格
                onebotMessageContentList.add(MessageContent.buildTextContent(" "));
            }else if (BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())) {
                //构建本地图片消息
                onebotMessageContentList.add(MessageContent.buildImageMessageContentFromBase64(FileUtils.fileToBase64((File) bbMessageContent.getData())));
            }else if (BbSendMessageType.NET_IMAGE.equals(bbMessageContent.getType())) {
                //构建网络图片消息
                onebotMessageContentList.add(MessageContent.buildImageMessageContentFromPath(bbMessageContent.getData().toString()));
            }
        }

        Action action = null;
        //根据消息类型封装群组或个人消息
        if (MessageType.GROUP.equals(bbSendMessage.getMessageType())) {
            action = Action.buildPrivateMessageSendAction(Message.builder()
                    .groupId(bbSendMessage.getGroupId())
                    .message(onebotMessageContentList)
                    .build());
        }else if (MessageType.PRIVATE.equals(bbSendMessage.getMessageType())) {
            action = Action.buildPrivateMessageSendAction(Message.builder()
                    .userId(bbSendMessage.getUserId())
                    .message(onebotMessageContentList)
                    .build());
        }

        //发送消息
        if (action != null && bbSendMessage.getWebSocket().isOpen()) {
            bbSendMessage.getWebSocket().send(JSON.toJSONString(action));
        }
    }

}
