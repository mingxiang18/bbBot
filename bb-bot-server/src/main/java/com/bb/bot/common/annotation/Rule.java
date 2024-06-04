package com.bb.bot.common.annotation;

import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.MessageType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.constant.SyncType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 触发规则注解
 * @author ren
 */
@Target(value = {ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Rule {

    /**
     * 事件类型
     * 详见常量类EventType
     */
    String eventType() default EventType.MESSAGE;

    /**
     * 消息类型
     * 详见常量类MessageType
     */
    String messageType() default MessageType.ALL;

    /**
     * 规则类型
     * 详见常量类RuleType
     */
    String ruleType() default RuleType.MATCH;

    /**
     * 规则关键字
     */
    String[] keyword() default {};

    /**
     * 规则名称
     */
    String name();

    /**
     * 是否需要@自己
     */
    boolean needAtMe() default false;

    /**
     * 是否需要同步执行（默认异步）
     * 如果是同步，则后面的匹配规则需要等待该方法执行后再运行，否则（异步情况）是并发执行
     */
    String syncType() default SyncType.ASYNC;
}
