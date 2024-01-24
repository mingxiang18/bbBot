package com.bb.bot.dispatcher.qqnt;

import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.config.BotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.common.constant.MessageType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.constant.SyncType;
import com.bb.bot.entity.qqnt.MessageElement;
import com.bb.bot.entity.qqnt.QqntReceiveMessage;
import com.bb.bot.event.qqnt.ReceiveMessageEvent;
import com.bb.bot.util.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 机器人事件分发者
 * @author ren
 */
@Slf4j
@Component
public class QqntEventDispatcher {

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private ThreadPoolTaskExecutor eventHandlerExecutor;

    private Map<Method, Object> messageHandlerMap = new LinkedHashMap<>();

    /**
     * 机器人事件分发者构造函数
     */
    public QqntEventDispatcher() {
        //获取所有包含BootEventHandler注解的机器人事件处理者的Bean
        Map<String, Object> beansWithRule = SpringUtils.getBeansWithAnnotation(BootEventHandler.class);
        //按order排序并封装成List
        List<Object> handlerBeanList = beansWithRule.entrySet().stream().map(Map.Entry::getValue)
                .filter(o -> BotType.QQNT.equals(AnnotationUtils.findAnnotation(o.getClass(), BootEventHandler.class).botType()))
                .sorted(Comparator.comparing(o -> {
                    return AnnotationUtils.findAnnotation(o.getClass(), BootEventHandler.class).order();
                })).collect(Collectors.toList());

        //遍历
        for (Object handlerObject : handlerBeanList) {
            //反射获取机器人事件处理者的所有方法
            Method[] declaredMethods = handlerObject.getClass().getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                //如果方法中包含Rule注解
                Rule annotation = AnnotationUtils.findAnnotation(declaredMethod, Rule.class);
                if (annotation == null) {
                    continue;
                }

                //将对应类型的事件放置到对应Map
                messageHandlerMap.put(declaredMethod, handlerObject);
            }
        }
    }

    /**
     * 机器人消息事件分发
     */
    public void handleMessage(ReceiveMessageEvent messageEvent) {
        for (Map.Entry<Method, Object> entry : messageHandlerMap.entrySet()) {
            Rule rule = AnnotationUtils.findAnnotation(entry.getKey(), Rule.class);

            if (SyncType.SYNC.equals(rule.syncType())) {
                //如果执行类型是同步执行，则进行同步调用
                if (messageRuleMatch(messageEvent, rule)) {
                    handlerExecute(entry.getKey(), entry.getValue(), messageEvent);
                }
            }else if (SyncType.ASYNC.equals(rule.syncType())) {
                //如果执行类型是异步执行, 则通过线程池异步执行消息处理
                eventHandlerExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (messageRuleMatch(messageEvent, rule)) {
                            handlerExecute(entry.getKey(), entry.getValue(), messageEvent);
                        }
                    }
                });
            }
        }
    }

    /**
     * 通用的事件执行方法
     */
    private void handlerExecute(Method method, Object instance, Object event) {
        try {
            method.invoke(instance, event);
        } catch (Exception e) {
            log.error("机器人事件处理者: " + method.getClass().getName() + "." + method.getName() + ", 处理出现异常", e);
        }
    }

    /**
     * 判断消息是否匹配规则
     */
    private boolean messageRuleMatch(ReceiveMessageEvent messageEvent, Rule rule) {
        QqntReceiveMessage receiveMessage = messageEvent.getData();
        List<MessageElement> elementList = !CollectionUtils.isEmpty(receiveMessage.getRaw().getElements()) ? receiveMessage.getRaw().getElements() : new ArrayList<>();
        //判断是否命中@规则
        if (rule.needAtMe() && "group".equals(receiveMessage.getPeer().getChatType())) {
            boolean match = false;
            for (MessageElement messageElement : elementList) {
                if (Integer.valueOf(1).equals(messageElement.getElementType())
                        && botConfig.getQq().equals(messageElement.getTextElement().getAtUid())) {
                    match = true;
                    break;
                }
            }

            //如果没有匹配成功则返回false
            if (!match) {
                return false;
            }
        }

        //判断消息类型
        if (!MessageType.ALL.equals(rule.messageType())) {
            if (MessageType.GROUP.equals(rule.messageType()) && !"group".equals(receiveMessage.getPeer().getChatType())) {
                return false;
            }

            if (MessageType.PRIVATE.equals(rule.messageType()) && !"friend".equals(receiveMessage.getPeer().getChatType())) {
                return false;
            }
        }

        //判断规则类型
        //如果没有关键字，则默认匹配成功
        if (rule.keyword() == null || rule.keyword().length == 0) {
            return true;
        }
        for (MessageElement messageElement : elementList) {
            if (Integer.valueOf(1).equals(messageElement.getElementType())) {
                String message = messageElement.getTextElement().getContent();
                if (RuleType.MATCH.equals(rule.ruleType())) {
                    for (String keyword : rule.keyword()) {
                        if (message.equals(keyword)) {
                            return true;
                        }
                    }
                } else if (RuleType.FUZZY.equals(rule.ruleType())) {
                    for (String keyword : rule.keyword()) {
                        if (message.contains(keyword)) {
                            return true;
                        }
                    }
                } else if (RuleType.REGEX.equals(rule.ruleType())) {
                    for (String keyword : rule.keyword()) {
                        Pattern compile = Pattern.compile(keyword);
                        Matcher matcher = compile.matcher(message);
                        while (matcher.find()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
