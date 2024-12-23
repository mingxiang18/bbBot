package com.bb.bot.api.qq;

import com.bb.bot.config.QqConfig;
import com.bb.bot.connection.qq.QqWebSocketClient;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.connection.qq.QqApiCaller;
import com.bb.bot.common.util.fileClient.FileClientApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Component
public class QqToBbMessageApi {

    @Autowired
    private QqApiCaller qqApiCaller;

    @Autowired
    private FileClientApi fileClientApi;

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        //转换为qq的qqWebSocket具体实现类
        QqWebSocketClient qqWebSocketClient = (QqWebSocketClient) bbSendMessage.getWebSocket();
        QqConfig qqConfig = qqWebSocketClient.getQqConfig();

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
                    try (FileInputStream inputStream = new FileInputStream((File) bbMessageContent.getData())) {
                        //本地图片上传后获取临时网络地址
                        channelMessage.setImage(fileClientApi.uploadTmpFile(inputStream));
                    }catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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
            qqApiCaller.sendChannelMessage(qqConfig, bbSendMessage.getGroupId(), channelMessage);
        }
    }

}
