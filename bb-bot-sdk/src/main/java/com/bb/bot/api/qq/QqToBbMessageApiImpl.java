package com.bb.bot.api.qq;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.handler.qq.QqApiCaller;
import com.bb.bot.util.imageUpload.ImageUploadApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;

@Component
@ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.QQ)
public class QqToBbMessageApiImpl implements BbMessageApi {

    @Autowired
    private QqApiCaller qqApiCaller;

    @Autowired
    private ImageUploadApi imageUploadApi;

    @Override
    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        //目前只适配了频道消息
        if (MessageType.GROUP.equals(bbSendMessage.getMessageType())) {
            //封装频道消息
            ChannelMessage channelMessage = new ChannelMessage();

            //消息内容
            StringBuilder messageContent = new StringBuilder();

            for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
                if (bbMessageContent.getType().equals(BbSendMessageType.AT)) {
                    //封装at消息
                    messageContent.append(ChannelMessage.buildAtMessage(bbMessageContent.getData().toString()));
                }else if (bbMessageContent.getType().equals(BbSendMessageType.TEXT)) {
                    //封装文本消息
                    messageContent.append(bbMessageContent.getData().toString());
                }else if (bbMessageContent.getType().equals(BbSendMessageType.LOCAL_IMAGE)) {
                    //本地图片上传后获取网络地址并设置
                    channelMessage.setImage(imageUploadApi.uploadImage((File) bbMessageContent.getData()));
                }else if (bbMessageContent.getType().equals(BbSendMessageType.NET_IMAGE)) {
                    //封装网络图片地址
                    channelMessage.setImage(bbMessageContent.getData().toString());
                }
            }

            //设置回复的原消息id
            channelMessage.setMsgId(bbSendMessage.getReceiveMessageId());
            //设置消息内容
            channelMessage.setContent(messageContent.toString());

            //调用qq接口发送消息
            qqApiCaller.sendChannelMessage(bbSendMessage.getGroupId(), channelMessage);
        }
    }

}
