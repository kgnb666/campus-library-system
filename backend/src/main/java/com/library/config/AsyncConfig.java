package com.library.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
     * 站内通知异步执行线程池 (Stage 10-I 调整队列容量)
     *
     * <p>核心线程数 2、最大线程数 8，阻塞队列容量由 500 降为 50。</p>
     *
     * <p><b>为什么要改队列容量</b>: {@code ThreadPoolTaskExecutor} 用的是 JDK 线程池语义 ——
     * 只有当队列<b>已满</b>时才会把线程数从 core 扩到 max。队列 500 意味着要先堆积 500 个任务
     * 才会启用第 3~8 个线程，等于"最大 8 线程"这个配置长期不可达，有效并发只有 2。
     * 队列降到 50 后，通知突发时能立刻扩容到 8 线程；队列本身也仍是可控的有界缓冲。</p>
     *
     * <p><b>为什么保留 CallerRunsPolicy</b>: 拒绝策略会在队列与线程池都打满时触发。
     * 通知写入是"提交后丢弃即永久丢失"的业务（催还/逾期/预约到馆提醒），
     * 丢弃比短暂占用调用方线程更糟；官方建议的 AbortPolicy 会直接把异常抛回
     * AFTER_COMMIT 事件发布方，而那时业务事务已提交，用户会看到"业务成功但接口报错"的错乱结果。
     * 因此保留 CallerRunsPolicy: 以调用线程兜底执行，形成天然背压，且不丢数据。</p>
     */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor(
            @Value("${app.async.notification.core-pool-size:2}") int corePoolSize,
            @Value("${app.async.notification.max-pool-size:8}") int maxPoolSize,
            @Value("${app.async.notification.queue-capacity:50}") int queueCapacity) {
        log.info("初始化站内通知专属异步线程池 [notificationExecutor]: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notification-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
