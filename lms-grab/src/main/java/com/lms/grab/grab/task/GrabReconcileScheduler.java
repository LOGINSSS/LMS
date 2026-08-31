package com.lms.grab.grab.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lms.grab.grab.domain.po.GrabRecord;
import com.lms.grab.grab.enums.GrabRecordStatus;
import com.lms.grab.grab.mapper.GrabRecordMapper;
import com.lms.grab.grab.service.GrabEventProducer;
import com.lms.grab.grab.service.GrabRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 抢课落库对账任务（spec 0.2 §4.5）
 *
 * 兜底策略：
 * - PENDING 超过 5 分钟：重发一次 Kafka（消费端幂等，安全重试）；
 * - PENDING 超过 15 分钟：判定落库失败，标记已回补并从 Redis 回补库存/移除去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabReconcileScheduler {

    private final GrabRecordMapper grabRecordMapper;
    private final GrabEventProducer eventProducer;
    private final GrabRedisService grabRedis;
    private final StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RETRY_MINUTES = 5;
    private static final int REFUND_MINUTES = 15;

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
            if (elapsed >= REFUND_MINUTES) {
                // 超时回补：标记已回补 + Redis 回补库存 + 移除去重
                grabRecordMapper.update(null, new LambdaUpdateWrapper<GrabRecord>()
                        .eq(GrabRecord::getId, r.getId())
                        .set(GrabRecord::getStatus, GrabRecordStatus.REFUNDED.getValue()));
                String stockKey = "grab:stock:" + r.getCourseId();
                redisTemplate.opsForValue().increment(stockKey);
                redisTemplate.opsForSet().remove("grab:users:" + r.getCourseId(), String.valueOf(r.getUserId()));
                log.warn("抢课落库超时回补 courseId={} userId={} recordId={}", r.getCourseId(), r.getUserId(), r.getId());
            } else if (elapsed >= RETRY_MINUTES) {
                // 重发一次 Kafka（消费端幂等）
                eventProducer.publishSuccess(r.getCourseId(), r.getUserId(),
                        r.getGrabTime().format(FMT), r.getId());
                log.info("抢课落库重试 courseId={} userId={} recordId={}", r.getCourseId(), r.getUserId(), r.getId());
            }
        }
    }
}
