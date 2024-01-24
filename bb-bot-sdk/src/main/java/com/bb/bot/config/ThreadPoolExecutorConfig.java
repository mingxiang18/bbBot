package com.bb.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池相关配置
 * @author ren
 */
@Slf4j
@Configuration
public class ThreadPoolExecutorConfig {

    @Value("${eventDispatcher.corePoolSize:5}")
    private int corePoolSize;

    @Value("${eventDispatcher.maxPoolSize:10}")
    private int maxPoolSize;

    @Value("${eventDispatcher.queueCapacity:1000}")
    private int queueCapacity;

    /**
     * 通用异步操作的异步线程池
     */
    @Bean
    public Executor eventDispatcherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数
        executor.setMaxPoolSize(maxPoolSize);
        // 队列大小
        executor.setQueueCapacity(queueCapacity);
        // 配置线程池中的线程的名称前缀
        executor.setThreadNamePrefix("event-dispatcher-");
        // 设置拒绝策略：当pool已经达到max size的时候，如何处理新任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        //执行初始化
        executor.initialize();
        return executor;
    }
}
