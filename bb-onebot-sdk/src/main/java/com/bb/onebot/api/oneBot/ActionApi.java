package com.bb.onebot.api.oneBot;

import com.bb.onebot.entity.oneBot.MessageContent;

import java.util.List;

/**
 * 发起动作请求Api
 * @author ren
 */
public interface ActionApi {

    /**
     * 发送私聊消息(文本)
     */
    public boolean sendPrivateMessage(String userId, String message);

    /**
     * 发送私聊消息
     */
    public boolean sendPrivateMessage(String userId, MessageContent messageContent);

    /**
     * 发送私聊消息
     */
    public boolean sendPrivateMessage(String userId, List<MessageContent> messageContentList);

    /**
     * 发送群聊消息(文本)
     */
    public boolean sendGroupMessage(String groupId, String message);

    /**
     * 发送群聊消息
     */
    public boolean sendGroupMessage(String groupId, MessageContent messageContent);

    /**
     * 发送群聊消息
     */
    public boolean sendGroupMessage(String groupId, List<MessageContent> messageContentList);
}
