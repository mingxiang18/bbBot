package com.bb.onebot.annotation;

import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.MessageType;
import com.bb.onebot.constant.RuleType;

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
    String[] keyword();

    /**
     * 规则名称
     */
    String name();

    /**
     * 是否需要@自己，仅群组消息会进行判断
     */
    boolean needAtMe() default false;
}
