package com.bb.bot.annotation;

import com.bb.bot.constant.BotType;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 机器人事件处理者注解
 * @author ren
 */

@Component
@Target(value = {ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BootEventHandler {

    /**
     * 排序
     */
    int order() default 50;

    /**
     * 机器人类型：oneBot、qqnt
     */
    String botType() default BotType.ONEBOT;
}
