package com.bb.bot.api.qq;

import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageContentVisitor;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.config.QqConfig;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.connection.qq.QqApiCaller;
import com.bb.bot.common.util.fileClient.FileClientApi;
import com.bb.bot.entity.qq.GroupMessage;
import com.bb.bot.entity.qq.UploadMediaRequest;
import com.bb.bot.entity.qq.UploadMediaResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class QqToBbMessageApi {

    @Autowired
    private QqApiCaller qqApiCaller;

    @Autowired
    private FileClientApi fileClientApi;

    /**
     * 群/单聊被动回复的 msg_seq 自增器，按被动消息的 msg_id 隔离。
     * QQ 对同一 msg_id 的多条被动回复按 (msg_id, msg_seq) 去重，msg_seq 重复会报 40054005，
     * 而一条 AI 回复常拆成「文本流 + 图片」多次发送，故由本侧按 msg_id 自增分配，保证唯一。
     * 被动回复窗口最长 60 分钟，10 分钟过期足够回收。
     */
    private static final Cache<String, AtomicInteger> MSG_SEQ_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    /**
     * 取指定被动消息 msg_id 的下一个 msg_seq（从 1 开始递增）。msg_id 为空时退回 1。
     */
    private int nextMsgSeq(String msgId) {
        if (StringUtils.isBlank(msgId)) {
            return 1;
        }
        return MSG_SEQ_CACHE.get(msgId, k -> new AtomicInteger(0)).incrementAndGet();
    }

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

        MessageContentVisitor.forEachContent(bbSendMessage,
                //封装文本消息
                messageContent::append,
                localImage -> {
                    try (FileInputStream inputStream = new FileInputStream(localImage)) {
                        //本地图片上传后获取临时网络地址
                        UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(fileClientApi.uploadTmpFile(inputStream));
                        UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadGroupMedia(qqConfig, bbSendMessage.getGroupId(), imageRequest);

                        groupMessage.setMsgType(7);
                        groupMessage.setMedia(uploadMediaResponse);
                    } catch (IOException e) {
                        log.error("本地图片上传失败 group={} user={}", bbSendMessage.getGroupId(), bbSendMessage.getUserId(), e);
                        throw new RuntimeException(e);
                    }
                },
                //群消息不处理 AT
                null,
                netImageUrl -> {
                    //封装网络图片地址
                    UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(netImageUrl);
                    UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadGroupMedia(qqConfig, bbSendMessage.getGroupId(), imageRequest);

                    groupMessage.setMsgType(7);
                    groupMessage.setMedia(uploadMediaResponse);
                });

        //设置回复的原消息id
        groupMessage.setMsgId(bbSendMessage.getReceiveMessageId());
        groupMessage.setMsgSeq(String.valueOf(nextMsgSeq(bbSendMessage.getReceiveMessageId())));
        //设置消息内容
        groupMessage.setContent(messageContent.toString());

        //调用qq接口发送消息
        qqApiCaller.sendGroupMessage(qqConfig, bbSendMessage.getGroupId(), groupMessage);
        log.info("QQ 群消息已投递 group={} replyTo={} msgType={}", bbSendMessage.getGroupId(),
                groupMessage.getMsgId(), groupMessage.getMsgType());
    }

    private void sendChannelMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
        //封装频道消息
        ChannelMessage channelMessage = new ChannelMessage();

        //消息内容
        StringBuilder messageContent = new StringBuilder();

        MessageContentVisitor.forEachContent(bbSendMessage,
                //封装文本消息
                messageContent::append,
                localImage -> {
                    try (FileInputStream inputStream = new FileInputStream(localImage)) {
                        //本地图片上传后获取临时网络地址
                        channelMessage.setImage(fileClientApi.uploadTmpFile(inputStream));
                    } catch (IOException e) {
                        log.error("本地图片上传失败 group={} user={}", bbSendMessage.getGroupId(), bbSendMessage.getUserId(), e);
                        throw new RuntimeException(e);
                    }
                },
                //封装at消息
                userId -> messageContent.append(ChannelMessage.buildAtMessage(userId)),
                //封装网络图片地址
                channelMessage::setImage);

        //设置回复的原消息id
        channelMessage.setMsgId(bbSendMessage.getReceiveMessageId());
        //设置消息内容
        channelMessage.setContent(messageContent.toString());

        //调用qq接口发送消息
        qqApiCaller.sendChannelMessage(qqConfig, bbSendMessage.getGroupId(), channelMessage);
        log.info("QQ 子频道消息已投递 channel={} replyTo={}", bbSendMessage.getGroupId(), channelMessage.getMsgId());
    }

    /**
     * 发送单聊（C2C 普通私聊）消息，报文结构与群组消息一致，目标为用户 user_openid。
     */
    private void sendC2CMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setMsgType(0);

        //消息内容
        StringBuilder messageContent = new StringBuilder();

        MessageContentVisitor.forEachContent(bbSendMessage,
                //封装文本消息
                messageContent::append,
                localImage -> {
                    try (FileInputStream inputStream = new FileInputStream(localImage)) {
                        //本地图片上传后获取临时网络地址
                        UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(fileClientApi.uploadTmpFile(inputStream));
                        UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadC2CMedia(qqConfig, bbSendMessage.getUserId(), imageRequest);

                        groupMessage.setMsgType(7);
                        groupMessage.setMedia(uploadMediaResponse);
                    } catch (IOException e) {
                        log.error("本地图片上传失败 group={} user={}", bbSendMessage.getGroupId(), bbSendMessage.getUserId(), e);
                        throw new RuntimeException(e);
                    }
                },
                //单聊不处理 AT
                null,
                netImageUrl -> {
                    //封装网络图片地址
                    UploadMediaRequest imageRequest = UploadMediaRequest.buildImageRequest(netImageUrl);
                    UploadMediaResponse uploadMediaResponse = qqApiCaller.uploadC2CMedia(qqConfig, bbSendMessage.getUserId(), imageRequest);

                    groupMessage.setMsgType(7);
                    groupMessage.setMedia(uploadMediaResponse);
                });

        //设置回复的原消息id
        groupMessage.setMsgId(bbSendMessage.getReceiveMessageId());
        groupMessage.setMsgSeq(String.valueOf(nextMsgSeq(bbSendMessage.getReceiveMessageId())));
        //设置消息内容
        groupMessage.setContent(messageContent.toString());

        //调用qq接口发送消息
        qqApiCaller.sendC2CMessage(qqConfig, bbSendMessage.getUserId(), groupMessage);
        log.info("QQ 单聊消息已投递 user={} replyTo={} msgType={}", bbSendMessage.getUserId(),
                groupMessage.getMsgId(), groupMessage.getMsgType());
    }

    /**
     * 发送频道私信消息，报文结构与子频道消息一致，目标为 guild_id（存于 groupId）。
     */
    private void sendDirectMessage(BbSendMessage bbSendMessage, QqConfig qqConfig) {
        ChannelMessage channelMessage = new ChannelMessage();

        //消息内容
        StringBuilder messageContent = new StringBuilder();

        MessageContentVisitor.forEachContent(bbSendMessage,
                //封装文本消息
                messageContent::append,
                localImage -> {
                    try (FileInputStream inputStream = new FileInputStream(localImage)) {
                        //本地图片上传后获取临时网络地址
                        channelMessage.setImage(fileClientApi.uploadTmpFile(inputStream));
                    } catch (IOException e) {
                        log.error("本地图片上传失败 group={} user={}", bbSendMessage.getGroupId(), bbSendMessage.getUserId(), e);
                        throw new RuntimeException(e);
                    }
                },
                //封装at消息
                userId -> messageContent.append(ChannelMessage.buildAtMessage(userId)),
                //封装网络图片地址
                channelMessage::setImage);

        //设置回复的原消息id
        channelMessage.setMsgId(bbSendMessage.getReceiveMessageId());
        //设置消息内容
        channelMessage.setContent(messageContent.toString());

        //调用qq接口发送消息
        qqApiCaller.sendDirectMessage(qqConfig, bbSendMessage.getGroupId(), channelMessage);
        log.info("QQ 频道私信已投递 guild={} replyTo={}", bbSendMessage.getGroupId(), channelMessage.getMsgId());
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
