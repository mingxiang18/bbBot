package com.bb.bot.api.qq;

import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.config.QqConfig;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.connection.qq.QqApiCaller;
import com.bb.bot.common.util.fileClient.FileClientApi;
import com.bb.bot.entity.qq.GroupMessage;
import com.bb.bot.entity.qq.UploadMediaRequest;
import com.bb.bot.entity.qq.UploadMediaResponse;
import org.apache.commons.lang3.StringUtils;
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
        QqConfig qqConfig = (QqConfig) bbSendMessage.getConfig();

        if (MessageType.CHANNEL.equals(bbSendMessage.getMessageType())) {
            sendChannelMessage(bbSendMessage, qqConfig);
        }else if (MessageType.GROUP.equals(bbSendMessage.getMessageType())) {
            sendGroupMessage(bbSendMessage, qqConfig);
        }else if (MessageType.PRIVATE.equals(bbSendMessage.getMessageType())) {
            //私聊分两种：有 groupId(guild_id) 的是频道私信，走 /dms；否则是 C2C 单聊，走 /v2/users
            if (StringUtils.isNotBlank(bbSendMessage.getGroupId())) {
                sendDirectMessage(bbSendMessage, qqConfig);
            }else {
                sendC2CMessage(bbSendMessage, qqConfig);
            }
        }
    }

    private void sendGroupMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
        //封装频道消息
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setMsgType(0);

        //消息内容
        StringBuilder messageContent = new StringBuilder();

        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (bbMessageContent.getType().equals(BbSendMessageType.TEXT)) {
                //封装文本消息
                messageContent.append(bbMessageContent.getData().toString());
            }else if (bbMessageContent.getType().equals(BbSendMessageType.LOCAL_IMAGE)) {
                try (FileInputStream inputStream = new FileInputStream((File) bbMessageContent.getData())) {
                    //本地图片上传后获取临时网络地址
                    UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(fileClientApi.uploadTmpFile(inputStream));
                    UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadGroupMedia(qqConfig, bbSendMessage.getGroupId(), imageRequest);

                    groupMessage.setMsgType(7);
                    groupMessage.setMedia(uploadMediaResponse);
                }catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }else if (bbMessageContent.getType().equals(BbSendMessageType.NET_IMAGE)) {
                //封装网络图片地址
                UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(bbMessageContent.getData().toString());
                UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadGroupMedia(qqConfig, bbSendMessage.getGroupId(), imageRequest);

                groupMessage.setMsgType(7);
                groupMessage.setMedia(uploadMediaResponse);
            }
        }

        //设置回复的原消息id
        groupMessage.setMsgId(bbSendMessage.getReceiveMessageId());
        groupMessage.setMsgSeq(String.valueOf(bbSendMessage.getMessageSeq()));
        //设置消息内容
        groupMessage.setContent(messageContent.toString());

        //调用qq接口发送消息
        qqApiCaller.sendGroupMessage(qqConfig, bbSendMessage.getGroupId(), groupMessage);
    }

    private void sendChannelMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
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

    /**
     * 发送单聊（C2C 普通私聊）消息，报文结构与群组消息一致，目标为用户 user_openid。
     */
    private void sendC2CMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setMsgType(0);

        //消息内容
        StringBuilder messageContent = new StringBuilder();

        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (bbMessageContent.getType().equals(BbSendMessageType.TEXT)) {
                //封装文本消息
                messageContent.append(bbMessageContent.getData().toString());
            }else if (bbMessageContent.getType().equals(BbSendMessageType.LOCAL_IMAGE)) {
                try (FileInputStream inputStream = new FileInputStream((File) bbMessageContent.getData())) {
                    //本地图片上传后获取临时网络地址
                    UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(fileClientApi.uploadTmpFile(inputStream));
                    UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadC2CMedia(qqConfig, bbSendMessage.getUserId(), imageRequest);

                    groupMessage.setMsgType(7);
                    groupMessage.setMedia(uploadMediaResponse);
                }catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }else if (bbMessageContent.getType().equals(BbSendMessageType.NET_IMAGE)) {
                //封装网络图片地址
                UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(bbMessageContent.getData().toString());
                UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadC2CMedia(qqConfig, bbSendMessage.getUserId(), imageRequest);

                groupMessage.setMsgType(7);
                groupMessage.setMedia(uploadMediaResponse);
            }
        }

        //设置回复的原消息id
        groupMessage.setMsgId(bbSendMessage.getReceiveMessageId());
        groupMessage.setMsgSeq(String.valueOf(bbSendMessage.getMessageSeq()));
        //设置消息内容
        groupMessage.setContent(messageContent.toString());

        //调用qq接口发送消息
        qqApiCaller.sendC2CMessage(qqConfig, bbSendMessage.getUserId(), groupMessage);
    }

    /**
     * 发送频道私信消息，报文结构与子频道消息一致，目标为 guild_id（存于 groupId）。
     */
    private void sendDirectMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
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
        qqApiCaller.sendDirectMessage(qqConfig, bbSendMessage.getGroupId(), channelMessage);
    }

    /**
     * QQ 官方 Bot API 没有 edit 接口，且被动消息有 5 条上限。
     * 强行 chunked-send 会被官方截断 + 体感差，所以 QQ 走 fallback：
     * 累积所有 delta 到 buffer，complete() 时一次性 sendMessage。
     * 等真正的流式接口（如有）出来再升级。
     */
    public MessageStreamSession startStream(BbSendMessage bbSendMessage) {
        return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
    }

}
