package com.library;

import com.library.scheduler.BorrowDueCheckExecutor;
import com.library.scheduler.BorrowDueCheckScheduler;
import com.library.scheduler.RedisSchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 调度器职责测试 (Stage 10-F)
 *
 * <p>调度器现在只负责"何时执行、是否由本实例执行"：抢占分布式锁、委托执行体、
 * 释放锁。业务断言见 {@link BorrowDueCheckExecutorTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class BorrowDueCheckSchedulerTest {

    @Mock
    private BorrowDueCheckExecutor executor;
    @Mock
    private RedisSchedulerLock schedulerLock;

    @InjectMocks
    private BorrowDueCheckScheduler scheduler;

    @Test
    @DisplayName("未抢占到分布式锁时跳过执行（避免多实例重复推送通知）")
    void skipsExecutionWhenLockNotAcquired() {
        when(schedulerLock.tryAcquire(anyString(), any(Duration.class))).thenReturn(null);

        scheduler.runDueCheckTask();

        verifyNoInteractions(executor);
        verify(schedulerLock, never()).release(anyString(), anyString());
    }

    @Test
    @DisplayName("抢占到锁时执行巡检并在结束后释放锁")
    void executesAndReleasesLockWhenAcquired() {
        when(schedulerLock.tryAcquire(anyString(), any(Duration.class))).thenReturn("token-1");
        when(executor.scanAndProcessOverdueAndReminders())
                .thenReturn(new BorrowDueCheckExecutor.TaskSummary(2, 1, 3, 3, 2, 0));

        scheduler.runDueCheckTask();

        verify(executor, times(1)).scanAndProcessOverdueAndReminders();
        verify(schedulerLock, times(1)).release(anyString(), eq("token-1"));
    }

    @Test
    @DisplayName("执行体抛异常时不向外冒泡且仍释放锁")
    void releasesLockEvenWhenExecutorFails() {
        when(schedulerLock.tryAcquire(anyString(), any(Duration.class))).thenReturn("token-2");
        when(executor.scanAndProcessOverdueAndReminders())
                .thenThrow(new RuntimeException("模拟任务异常"));

        // 调度线程不应因任务异常而中断
        scheduler.runDueCheckTask();

        verify(schedulerLock, times(1)).release(anyString(), eq("token-2"));
    }
}
