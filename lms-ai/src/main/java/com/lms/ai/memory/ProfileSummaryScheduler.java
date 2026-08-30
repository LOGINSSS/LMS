package com.lms.ai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 画像摘要周期压缩（spec §4.3：每日/每 N 条事件触发一次 summary 全量压缩）
 *
 * 每小时扫描待执行的画像生成任务（agent_profile_job），低频执行控制成本（spec §10）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lms.ai.agent", name = "enabled", havingValue = "true")
public class ProfileSummaryScheduler {

    private final ProfileService profileService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void runPending() {
        try {
            int n = profileService.runPendingSummaries();
            if (n > 0) {
                log.info("本轮画像摘要生成 {} 个", n);
            }
        } catch (Exception e) {
            log.error("画像摘要调度异常", e);
        }
    }
}
