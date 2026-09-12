package com.lms.grab.grab.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.grab.grab.domain.po.GrabRecord;
import com.lms.grab.grab.enums.GrabRecordStatus;
import com.lms.grab.grab.mapper.GrabRecordMapper;
import com.lms.grab.grab.service.GrabEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 抢课落库对账任务（spec 0.2 §4.5）
 *
 * 兜底策略：
 * - PENDING 超过 5 分钟：重发 Kafka（消费端幂等，安全重试）；
 * - 不按时间自动回补库存。仅凭消息超时无法证明课程侧没有落库，自动回补会与迟到消息
 *   形成“库存已恢复但选课仍落库”的超卖竞态；回补必须先以 course_enrollment 为准做权威对账。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabReconcileScheduler {

    private final GrabRecordMapper grabRecordMapper;
    private final GrabEventProducer eventProducer;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RETRY_MINUTES = 5;

    /** 每 5 分钟扫描一次待落库记录 */
    @Scheduled(fixedDelay = 300000)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        List<GrabRecord> pending = grabRecordMapper.selectList(new LambdaQueryWrapper<GrabRecord>()
                .eq(GrabRecord::getStatus, GrabRecordStatus.PENDING.getValue()));
        if (pending.isEmpty()) {
            return;
        }
        for (GrabRecord r : pending) {
            long elapsed = java.time.Duration.between(r.getGrabTime(), now).toMinutes();
            if (elapsed >= RETRY_MINUTES) {
                // 持续重发 Kafka（消费端唯一键幂等）；收到课程侧回执后状态转为 LANDED，退出扫描。
                eventProducer.publishSuccess(r.getCourseId(), r.getUserId(),
                        r.getGrabTime().format(FMT), r.getId());
                log.warn("抢课落库尚未收到回执，已重试 courseId={} userId={} recordId={} pendingMinutes={}",
                        r.getCourseId(), r.getUserId(), r.getId(), elapsed);
            }
        }
    }
}
