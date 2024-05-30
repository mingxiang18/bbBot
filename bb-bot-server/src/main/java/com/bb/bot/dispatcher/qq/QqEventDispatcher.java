package com.bb.bot.dispatcher.qq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.config.BotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.constant.SyncType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.qq.QqMessage;
import com.bb.bot.util.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

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
public class QqEventDispatcher {

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private ThreadPoolTaskExecutor eventHandlerExecutor;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    /**
     * 消息处理方法Map
     */
    private Map<Method, Object> messageHandlerMap = new LinkedHashMap<>();
    /**
     * 默认消息处理方法Map
     */
    private Map<Method, Object> defaultHandlerMap = new LinkedHashMap<>();
    /**
     * 占用相关的消息处理方法Map
     */
    private Map<Method, Object> occupationHandlerMap = new LinkedHashMap<>();

    /**
     * 用于@的cq码正则
     */
    public final static String AT_COMPILE_REG = "<@.*?>\\s?";

    /**
     * 机器人事件分发者构造函数
     */
    public QqEventDispatcher() {
        //获取所有包含BootEventHandler注解的机器人事件处理者的Bean
        Map<String, Object> beansWithRule = SpringUtils.getBeansWithAnnotation(BootEventHandler.class);
        //按order排序并封装成List
        List<Object> handlerBeanList = beansWithRule.entrySet().stream().map(Map.Entry::getValue)
                .filter(o -> BotType.QQ.equals(AnnotationUtils.findAnnotation(o.getClass(), BootEventHandler.class).botType()))
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

                if (RuleType.DEFAULT.equals(annotation.ruleType())) {
                    //将默认事件放置到默认Map
                    defaultHandlerMap.put(declaredMethod, handlerObject);
                }else if (RuleType.OCCUPATION.equals(annotation.ruleType())) {
                    //将占用事件放置到对应Map
                    occupationHandlerMap.put(declaredMethod, handlerObject);
                }else {
                    //将对应类型的事件放置到对应Map
                    messageHandlerMap.put(declaredMethod, handlerObject);
                }
            }
        }
    }

    /**
     * 机器人消息事件分发
     */
    public void handleMessage(QqMessage messageEvent) {
        //查询当前群组是否存在占用的方法
        UserConfigValue useMethod = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, messageEvent.getChannelId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION));
        //如果有，所有消息都由该方法接管
        if (useMethod != null) {
            for (Map.Entry<Method, Object> entry : occupationHandlerMap.entrySet()) {
                if (entry.getKey().getName().equals(useMethod.getKeyName())) {
                    handlerExecute(entry.getKey(), entry.getValue(), messageEvent);
                    return;
                }
            }
            //如果没找到占用方法，抛出异常
            throw new RuntimeException("未找到当前占用的处理方法");
        }

        //是否存在匹配的处理者
        boolean matchFlag = false;
        //遍历查找所有对应的处理者方法进行处理
        for (Map.Entry<Method, Object> entry : messageHandlerMap.entrySet()) {
            Rule rule = AnnotationUtils.findAnnotation(entry.getKey(), Rule.class);

            if (SyncType.SYNC.equals(rule.syncType())) {
                //如果执行类型是同步执行，则进行同步调用
                if (messageRuleMatch(messageEvent, rule)) {
                    handlerExecute(entry.getKey(), entry.getValue(), messageEvent);
                    //如果规则关键字不为空，说明匹配到了规则
                    if (rule.keyword() != null && rule.keyword().length > 0) {
                        matchFlag = true;
                    }
                }
            }else if (SyncType.ASYNC.equals(rule.syncType())) {
                //如果执行类型是异步执行, 则通过线程池异步执行消息处理
                if (messageRuleMatch(messageEvent, rule)) {
                    eventHandlerExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            handlerExecute(entry.getKey(), entry.getValue(), messageEvent);
                        }
                    });
                    //如果规则关键字不为空，说明匹配到了规则
                    if (rule.keyword() != null && rule.keyword().length > 0) {
                        matchFlag = true;
                    }
                }
            }
        }

        //如果没有匹配到任何规则，则调用默认处理者进行回复
        if (!matchFlag) {
            for (Map.Entry<Method, Object> entry : defaultHandlerMap.entrySet()) {
                eventHandlerExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        handlerExecute(entry.getKey(), entry.getValue(), messageEvent);
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
    private boolean messageRuleMatch(QqMessage messageEvent, Rule rule) {
        //判断规则类型
        //如果没有关键字，则默认匹配成功
        if (rule.keyword() == null || rule.keyword().length == 0) {
            return true;
        }
        if (messageEvent.getContent() == null) {
            return false;
        }

        //如果需要@自己，判断消息体中是否有@机器人的参数
        if (rule.needAtMe()) {
            boolean atMe = false;
            List<QqMessage.QqUser> mentions = messageEvent.getMentions();
            for (QqMessage.QqUser mention : mentions) {
                if (mention.getBot()) {
                    atMe = true;
                    break;
                }
            }
            if (!atMe) {
                return false;
            }
        }

        //内容字符串移除开头的@机器人参数和末尾空行
        String message = messageEvent.getContent().replaceAll(AT_COMPILE_REG, "").replaceAll("\\s+$", "");
        messageEvent.setContent(message);
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

        return false;
    }
}
