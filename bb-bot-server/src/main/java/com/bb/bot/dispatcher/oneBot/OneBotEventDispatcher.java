package com.bb.bot.dispatcher.oneBot;

import com.bb.bot.annotation.BootEventHandler;
import com.bb.bot.annotation.Rule;
import com.bb.bot.config.BotConfig;
import com.bb.bot.constant.*;
import com.bb.bot.event.oneBot.ReceiveMessageEvent;
import com.bb.bot.event.oneBot.ReceiveNoticeEvent;
import com.bb.bot.util.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 机器人事件分发者
 * @author ren
 */
@Slf4j
@Component
public class OneBotEventDispatcher {

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private ThreadPoolTaskExecutor eventHandlerExecutor;

    private Map<Method, Object> messageHandlerMap = new LinkedHashMap<>();

    private Map<Method, Object> noticeHandlerMap = new LinkedHashMap<>();

    /**
     * 用于@某人的cq码正则
     */
    public static Pattern atCompile = Pattern.compile("\\[CQ:at.*?\\]");

    /**
     * 机器人事件分发者构造函数
     */
    public OneBotEventDispatcher() {
        //获取所有包含BootEventHandler注解的机器人事件处理者的Bean
        Map<String, Object> beansWithRule = SpringUtils.getBeansWithAnnotation(BootEventHandler.class);
        //按order排序并封装成List
        List<Object> handlerBeanList = beansWithRule.entrySet().stream().map(Map.Entry::getValue)
                .filter(o -> BotType.ONEBOT.equals(AnnotationUtils.findAnnotation(o.getClass(), BootEventHandler.class).botType()))
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
                if (EventType.MESSAGE.equals(annotation.eventType())) {
                    messageHandlerMap.put(declaredMethod, handlerObject);
                }else if (EventType.NOTICE.equals(annotation.eventType())) {
                    noticeHandlerMap.put(declaredMethod, handlerObject);
                }
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
     * 机器人提醒事件分发
     */
    public void handleNotice(ReceiveNoticeEvent noticeEvent) {
        for (Map.Entry<Method, Object> entry : noticeHandlerMap.entrySet()) {
            //通过线程池异步执行提醒处理
            eventHandlerExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    handlerExecute(entry.getKey(), entry.getValue(), noticeEvent);
                }
            });
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
        //判断是否命中@规则
        if (rule.needAtMe() && StringUtils.isNoneBlank(messageEvent.getData().getGroupId())) {
            Matcher matcher = atCompile.matcher(messageEvent.getData().getMessage());
            boolean match = false;

            //查找at匹配
            while (matcher.find()) {
                String atCQCode = matcher.group();
                //如果匹配项是自己的qq，则匹配成功
                if (atCQCode.contains(botConfig.getQq())) {
                    match = true;

                }
            }

            //如果没有匹配成功则返回false
            if (!match) {
                return false;
            }
        }

        //判断消息类型
        //todo 目前逻辑是没有group_id就属于私人消息，有就属于群组消息,可能逻辑不准确
        if (!MessageType.ALL.equals(rule.messageType())) {
            if (MessageType.GROUP.equals(rule.messageType())) {
                if (StringUtils.isBlank(messageEvent.getData().getGroupId())) {
                    return false;
                }
            }

            if (MessageType.PRIVATE.equals(rule.messageType())) {
                if (StringUtils.isNoneBlank(messageEvent.getData().getGroupId())) {
                    return false;
                }
            }
        }

        //判断规则类型
        //如果没有关键字，则默认匹配成功
        if (rule.keyword() == null || rule.keyword().length == 0) {
            return true;
        }
        //先将消息中的@字段的cq码去除
        String message = messageEvent.getData().getMessage().replaceAll("\\[CQ:at.*?\\]\\s?", "");
        if (RuleType.MATCH.equals(rule.ruleType())) {
            for (String keyword : rule.keyword()) {
                if (message.equals(keyword)) {
                    return true;
                }
            }
        }else if (RuleType.FUZZY.equals(rule.ruleType())) {
            for (String keyword : rule.keyword()) {
                if (message.contains(keyword)) {
                    return true;
                }
            }
        }else if (RuleType.REGEX.equals(rule.ruleType())) {
            for (String keyword : rule.keyword()) {
                Pattern compile = Pattern.compile(keyword);
                Matcher matcher = compile.matcher(message);
                while (matcher.find()) {
                    return true;
                }
            }
        }

        return false;
    }
}
