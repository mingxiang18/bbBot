package com.bb.bot.api.bb;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.bb.BbSocketServerMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;

@Component
public class BbToBbMessageApi {

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        //转换为bb的socket消息结构
        BbSocketServerMessage bbSocketServerMessage = new BbSocketServerMessage();
        BeanUtils.copyProperties(bbSendMessage, bbSocketServerMessage);
        bbSocketServerMessage.setMessageList(bbSendMessage.getMessageList());
        //将bb协议消息封装成socket消息结构
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())) {
                //构建本地图片消息，转换为base64返回
                bbMessageContent.setData((FileUtils.fileToBase64((File) bbMessageContent.getData())));
            }
        }

        //发送消息
        if (bbSendMessage.getWebSocket().isOpen()) {
            bbSendMessage.getWebSocket().send(JSON.toJSONString(bbSocketServerMessage));
        }
    }

}
