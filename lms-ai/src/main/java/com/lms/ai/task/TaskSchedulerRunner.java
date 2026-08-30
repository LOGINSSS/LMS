package com.lms.ai.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务调度扫描器（spec §6.2：落库 + @Scheduled 轮询执行，练手建议方案）
 *
 * 每 30s 扫描一次到期待执行任务（status=0 且 execute_time<=now），
 * 交给 TaskService 执行（结果回写 + 通知）。仅 lms.ai.task.enabled=true 时启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lms.ai.task", name = "enabled", havingValue = "true")
public class TaskSchedulerRunner {

    private final TaskService taskService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void pollDueTasks() {
        try {
            int n = taskService.executeDueTasks();
            if (n > 0) {
                log.info("本轮执行到点任务 {} 个", n);
            }
        } catch (Exception e) {
            log.error("任务扫描执行异常", e);
        }
    }
}
