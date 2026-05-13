package com.bb.bot.api.oneBot;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
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

    /**
     * OneBot 不支持改已发消息（标准协议无 edit），也没有「同一回复多次发文」的合理上限。
     * 强行 chunked-send 体感差 + 容易触发实现端（NapCat / Lagrange 等）限速。
     * 因此 OneBot 走 fallback：累积所有 delta 到 buffer，complete() 时一次性 sendMessage。
     * 用户体感跟 streamEnabled=false 一致：等完整回复再发，但走的是同一套上层调用。
     */
    public MessageStreamSession startStream(BbSendMessage bbSendMessage) {
        return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
    }
}
