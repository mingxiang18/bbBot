package com.bb.bot.handler.help;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.util.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 帮助事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, name = "帮助")
public class BbHelpHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"帮助", "/帮助"}, name = "帮助")
    public void helpHandle(BbReceiveMessage bbReceiveMessage) {
        StringBuilder helpContent = new StringBuilder();

        //获取所有包含BootEventHandler注解的机器人事件处理者的Bean
        Map<String, Object> beansWithRule = SpringUtils.getBeansWithAnnotation(BootEventHandler.class);
        //按order排序并封装成List
        for (Map.Entry<String, Object> entry : beansWithRule.entrySet()) {
            BootEventHandler bootEventHandler = AnnotationUtils.findAnnotation(entry.getValue().getClass(), BootEventHandler.class);
            if (bootEventHandler == null || !BotType.BB.equals(bootEventHandler.botType())) {
                continue;
            }
            helpContent.append("◉ ").append(bootEventHandler.name()).append("\n");
            //反射获取机器人事件处理者的所有方法
            Method[] declaredMethods = entry.getValue().getClass().getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                //如果方法中包含Rule注解
                Rule annotation = AnnotationUtils.findAnnotation(declaredMethod, Rule.class);
                if (annotation == null || annotation.keyword().length == 0) {
                    continue;
                }
                helpContent.append("--\n功能名：【").append(annotation.name()).append("】 \n指令：【")
                        .append(Arrays.stream(annotation.keyword())
                                .filter(StringUtils::isNoneBlank)
                                .collect(Collectors.joining("、")))
                        .append("】\n");
            }
            helpContent.append("\n");
        }

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(helpContent.toString())));
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
