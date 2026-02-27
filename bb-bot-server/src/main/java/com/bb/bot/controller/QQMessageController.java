package com.bb.bot.controller;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.common.util.security.Ed25519Signer;
import com.bb.bot.common.util.security.Ed25519Verifier;
import com.bb.bot.config.BotConfig;
import com.bb.bot.config.QqConfig;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.qq.QqCommonPayloadEntity;
import com.bb.bot.entity.qq.QqWebhookAuthRequest;
import com.bb.bot.entity.qq.QqWebhookAuthResponse;
import com.bb.bot.common.util.qq.QQMessageUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * qq回调控制器
 * @author ren
 */
@Slf4j
@RestController
@Api(tags = "qq回调Controller")
@RequestMapping("/qq")
public class QQMessageController {

    @Resource
    private BotConfig botConfig;

    @Autowired
    private ApplicationEventPublisher publisher;

    /**
     * qq webhook回调
     */
    @PostMapping(value = "/webhook")
    @ApiOperation(value="qq webhook回调")
    public Object webhook(@RequestHeader(value = "X-Bot-Appid") String botAppId,
                          @RequestHeader(value = "X-Signature-Ed25519") String signature,
                          @RequestHeader(value = "X-Signature-Timestamp") String timestamp,
                          @RequestBody byte[] body) throws Exception {
        log.info("收到qq webhook推送，appId：" + botAppId + "，报文：" + new String(body));
        QqConfig qqConfig = getQqConfig(botAppId);

        if (!Ed25519Verifier.verify(qqConfig.getClientSecret(), timestamp, body, signature)) {
            log.error("验证qq webhook失败，未知请求");
            return null;
        }
        QqCommonPayloadEntity qqCommonPayloadEntity = JSON.parseObject(body, QqCommonPayloadEntity.class);

        //验签
        if(Integer.valueOf(13).equals(qqCommonPayloadEntity.getOp())) {
            String secret = qqConfig.getClientSecret();
            QqWebhookAuthRequest qqWebhookAuthRequest = JSON.parseObject(JSON.toJSONString(qqCommonPayloadEntity.getD()), QqWebhookAuthRequest.class);
            String responseSignature = Ed25519Signer.generateSignature(secret, qqWebhookAuthRequest.getPlainToken(), qqWebhookAuthRequest.getEventTs());
            return new QqWebhookAuthResponse(qqWebhookAuthRequest.getPlainToken(), responseSignature);
        }else if (Integer.valueOf(0).equals(qqCommonPayloadEntity.getOp())) {
            if ("GROUP_AT_MESSAGE_CREATE".equals(qqCommonPayloadEntity.getT())) {
                BbReceiveMessage bbReceiveMessage = QQMessageUtil.formatBbReceiveMessageFromGroup(qqCommonPayloadEntity, qqConfig);
                //通过spring事件机制发布消息
                publisher.publishEvent(bbReceiveMessage);
            }else if ("AT_MESSAGE_CREATE".equals(qqCommonPayloadEntity.getT())) {
                BbReceiveMessage bbReceiveMessage = QQMessageUtil.formatBbReceiveMessageFromChannel(qqCommonPayloadEntity, qqConfig);
                //通过spring事件机制发布消息
                publisher.publishEvent(bbReceiveMessage);
            }

            QqCommonPayloadEntity response = new QqCommonPayloadEntity();
            response.setOp(12);
            response.setT(qqCommonPayloadEntity.getT());
            response.setS(qqCommonPayloadEntity.getS());
            return response;
        }else {
            throw new RuntimeException("op code not found: " + qqCommonPayloadEntity.getOp());
        }
    }

    private QqConfig getQqConfig(String botAppId) {
        QqConfig qqConfig = null;
        for (Map.Entry<String, QqConfig> qqConfigEntry : botConfig.getQq().entrySet()) {
            if (qqConfigEntry.getValue().isEnable()
                    && botAppId.equals(qqConfigEntry.getValue().getAppId())
                    && "webhook".equals(qqConfigEntry.getValue().getType()) ) {
                qqConfig = qqConfigEntry.getValue();
            }
        }
        if (qqConfig == null) {
            throw new RuntimeException("qq appId not found: " + botAppId);
        }
        return qqConfig;
    }
}
