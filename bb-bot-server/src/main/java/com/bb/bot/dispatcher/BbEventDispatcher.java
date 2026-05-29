package com.bb.bot.dispatcher;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.config.BotConfig;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.constant.SyncType;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.common.util.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 机器人事件分发者
 * @author ren
 */
@Slf4j
@Component
public class BbEventDispatcher {

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
     * REGEX 规则关键字 -> 预编译 {@link Pattern} 缓存。
     * 在 handler 注册时预编译一次，避免在每条消息匹配路径上重复 {@link Pattern#compile}。
     */
    private final RegexPatternCache regexPatternCache = new RegexPatternCache();

    /**
     * 机器人事件分发者构造函数
     */
    public BbEventDispatcher() {
        //获取所有包含BootEventHandler注解的机器人事件处理者的Bean
        Map<String, Object> beansWithRule = SpringUtils.getBeansWithAnnotation(BootEventHandler.class);
        //按order排序并封装成List
        List<Object> handlerBeanList = beansWithRule.entrySet().stream().map(Map.Entry::getValue)
                .filter(o -> BotType.BB.equals(AnnotationUtils.findAnnotation(o.getClass(), BootEventHandler.class).botType()))
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

                //如果是正则规则，注册时预编译关键字并缓存，避免每条消息重复编译
                if (RuleType.REGEX.equals(annotation.ruleType()) && annotation.keyword() != null) {
                    for (String keyword : annotation.keyword()) {
                        regexPatternCache.compile(keyword);
                    }
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
     * 机器人消息事件分发。
     *
     * @return 是否命中了某个「关键字命令」（占用 / 关键字规则）。命中即说明本条不是普通聊天，
     *         调用方可据此把记忆里的 inbound 事件降级，避免命令污染聊天上下文。
     */
    public boolean handleMessage(BbReceiveMessage bbReceiveMessage) {
        //内容字符串移除开头空格和末尾空格
        String message = bbReceiveMessage.getMessage().replaceAll("^\\s+", "").replaceAll("\\s+$", "");
        bbReceiveMessage.setMessage(message);

        //查询当前群组是否存在占用的方法
        UserConfigValue useMethod = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(MessageType.GROUP.equals(bbReceiveMessage.getMessageType()), UserConfigValue::getGroupId, bbReceiveMessage.getGroupId())
                .eq(MessageType.CHANNEL.equals(bbReceiveMessage.getMessageType()), UserConfigValue::getGroupId, bbReceiveMessage.getGroupId())
                .eq(MessageType.PRIVATE.equals(bbReceiveMessage.getMessageType()), UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                .eq(UserConfigValue::getType, RuleType.OCCUPATION));
        //如果有，所有消息都由该方法接管
        if (useMethod != null) {
            for (Map.Entry<Method, Object> entry : occupationHandlerMap.entrySet()) {
                Rule rule = AnnotationUtils.findAnnotation(entry.getKey(), Rule.class);
                //如果找到占用方法且匹配规则，执行后直接结束
                if (entry.getKey().getName().equals(useMethod.getKeyName()) && messageRuleMatch(bbReceiveMessage, rule)) {
                    handlerExecute(entry.getKey(), entry.getValue(), bbReceiveMessage);
                    return true;
                }
            }
        }

        //是否存在匹配的处理者
        boolean matchFlag = false;
        //遍历查找所有对应的处理者方法进行处理
        for (Map.Entry<Method, Object> entry : messageHandlerMap.entrySet()) {
            Rule rule = AnnotationUtils.findAnnotation(entry.getKey(), Rule.class);

            if (SyncType.SYNC.equals(rule.syncType())) {
                //如果执行类型是同步执行，则进行同步调用
                if (messageRuleMatch(bbReceiveMessage, rule)) {
                    handlerExecute(entry.getKey(), entry.getValue(), bbReceiveMessage);
                    //如果规则关键字不为空，说明匹配到了规则
                    if (rule.keyword() != null && rule.keyword().length > 0) {
                        matchFlag = true;
                    }
                }
            }else if (SyncType.ASYNC.equals(rule.syncType())) {
                //如果执行类型是异步执行, 则通过线程池异步执行消息处理
                if (messageRuleMatch(bbReceiveMessage, rule)) {
                    eventHandlerExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            handlerExecute(entry.getKey(), entry.getValue(), bbReceiveMessage);
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
                Rule rule = AnnotationUtils.findAnnotation(entry.getKey(), Rule.class);
                eventHandlerExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (messageRuleMatch(bbReceiveMessage, rule)) {
                            handlerExecute(entry.getKey(), entry.getValue(), bbReceiveMessage);
                        }
                    }
                });
            }
        }
        return matchFlag;
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
    boolean messageRuleMatch(BbReceiveMessage bbReceiveMessage, Rule rule) {
        //判断规则类型
        //如果是群组消息，且规则配置需要@自己，判断消息体中是否有@机器人的参数
        if ((MessageType.GROUP.equals(bbReceiveMessage.getMessageType()) || MessageType.CHANNEL.equals(bbReceiveMessage.getMessageType()))
                && rule.needAtMe()) {
            Optional<MessageUser> atMeFlag = bbReceiveMessage.getAtUserList().stream().filter(MessageUser::getBotFlag).findFirst();
            if (atMeFlag.isEmpty()) {
                return false;
            }
        }
        //如果没有关键字，则默认匹配成功
        if (rule.keyword() == null || rule.keyword().length == 0) {
            return true;
        }
        if (bbReceiveMessage.getMessage() == null) {
            return false;
        }

        String message = bbReceiveMessage.getMessage();
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
                //使用注册时预编译并缓存的 Pattern，避免每条消息重复编译
                Pattern compile = regexPatternCache.compile(keyword);
                Matcher matcher = compile.matcher(message);
                if (matcher.find()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 暴露给测试，校验某个正则关键字是否已被预编译缓存。
     */
    RegexPatternCache getRegexPatternCache() {
        return regexPatternCache;
    }

    /**
     * REGEX 规则关键字的预编译 {@link Pattern} 缓存。
     * <p>同一关键字只会编译一次（{@link #compile(String)} 幂等），后续命中直接返回缓存实例。
     * 内置编译计数器，便于单测验证"同规则多次匹配只编译一次"。
     */
    static final class RegexPatternCache {

        private final Map<String, Pattern> cache = new ConcurrentHashMap<>();

        /**
         * 编译计数器，仅在真正发生 {@link Pattern#compile} 时自增。
         */
        private int compileCount = 0;

        /**
         * 返回关键字对应的已编译 {@link Pattern}；缓存未命中时编译一次并缓存。
         */
        Pattern compile(String keyword) {
            Pattern cached = cache.get(keyword);
            if (cached != null) {
                return cached;
            }
            return cache.computeIfAbsent(keyword, k -> {
                compileCount++;
                return Pattern.compile(k);
            });
        }

        /**
         * 实际执行 {@link Pattern#compile} 的次数（测试用）。
         */
        int getCompileCount() {
            return compileCount;
        }
    }
}
