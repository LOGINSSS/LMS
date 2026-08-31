package com.lms.grab.grab.service;

import com.lms.grab.grab.config.GrabProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 抢课成功事件生产者（spec 0.2 §4.3）
 *
 * Redis 预检成功后发 Kafka topic（默认 lms-grab-success），
 * lms-course 消费端幂等落库 course_enrollment。
 * 消息 value 为 JSON：{"courseId":..,"userId":..,"grabTime":"..","grabRecordId":..}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final GrabProperties properties;

    public void publishSuccess(Long courseId, Long userId, String grabTime, Long grabRecordId) {
        String message = String.format(
                "{\"courseId\":%d,\"userId\":%d,\"grabTime\":\"%s\",\"grabRecordId\":%d}",
                courseId, userId, grabTime, grabRecordId);
        try {
            // key=courseId，保证同一课程的落库消息有序
            kafkaTemplate.send(properties.getTopic(), String.valueOf(courseId), message)
                    .whenComplete((r, e) -> {
                        if (e != null) {
                            log.warn("抢课成功事件发送失败 courseId={} userId={}: {}", courseId, userId, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("抢课成功事件发送异常 courseId={} userId={}: {}", courseId, userId, e.getMessage());
        }
    }
}
