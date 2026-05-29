package com.bb.bot.dispatcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * T10.2：验证 {@code handlerExecute} 的日志类名修正与 {@code InvocationTargetException} 取 cause。
 * <p>覆盖：
 * <ul>
 *   <li>handler 抛业务异常时，日志含真实 handler 类名（{@code instance.getClass().getSimpleName()}）+ 方法名，
 *       而不是 {@code java.lang.reflect.Method}；</li>
 *   <li>日志记录的 throwable 是 handler 抛出的原始异常（{@code InvocationTargetException.getCause()}），
 *       而非反射包装异常。</li>
 * </ul>
 */
class BbEventDispatcherHandlerExecuteTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger dispatcherLogger;

    /**
     * 会抛出业务异常的样例 handler。
     */
    @SuppressWarnings("unused")
    static class BoomHandler {
        static final RuntimeException BOOM = new IllegalStateException("handler 内部炸了");

        public void handle(Object event) {
            throw BOOM;
        }
    }

    @BeforeEach
    void setUp() {
        dispatcherLogger = (Logger) LoggerFactory.getLogger(BbEventDispatcher.class);
        appender = new ListAppender<>();
        appender.start();
        dispatcherLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        dispatcherLogger.detachAppender(appender);
    }

    private BbEventDispatcher newDispatcher() {
        // 不走 Spring 构造函数，仅对真实方法调用
        return mock(BbEventDispatcher.class, CALLS_REAL_METHODS);
    }

    @Test
    void logs_real_handler_class_name_and_original_cause_when_handler_throws() throws Exception {
        BbEventDispatcher dispatcher = newDispatcher();
        BoomHandler handler = new BoomHandler();
        Method method = BoomHandler.class.getMethod("handle", Object.class);

        dispatcher.handlerExecute(method, handler, new Object());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);

        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        // 日志含真实 handler 类名（简单名）+ 方法名，而不是 reflect.Method
        assertThat(event.getFormattedMessage())
                .contains("BoomHandler.handle")
                .doesNotContain("java.lang.reflect.Method");

        // 记录的是原始业务异常，而不是反射包装的 InvocationTargetException
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getClassName())
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("handler 内部炸了");
    }
}
