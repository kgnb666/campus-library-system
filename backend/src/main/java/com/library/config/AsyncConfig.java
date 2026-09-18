package com.library.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务与线程池配置 (Stage 9-D)
 * 提供领域事件通知异步解耦专用线程池，避免通知入库阻塞主业务事务连接
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    /**
     * 站内通知异步执行线程池
     * - 核心线程数: 2
     * - 最大线程数: 8
     * - 阻塞队列容量: 500
     * - 拒绝策略: CallerRunsPolicy (队列满时由调用线程兜底执行，保证通知不丢失)
     */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        log.info("初始化站内通知专属异步线程池 [notificationExecutor]: corePoolSize=2, maxPoolSize=8, queueCapacity=500");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
