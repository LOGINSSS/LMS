package com.lms.exam.exam.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 考试提交生产者（前端轨 §13.3：锁页交卷/中断自动交卷 → topic lms-exam-submission）
 *
 * key=scheduleId 保证同排期提交有序；至少一次投递由消费端 submission_id 唯一键幂等兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamSubmissionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${lms.exam.submission-topic:lms-exam-submission}")
    private String topic;

    /**
     * 发送考试提交消息
     *
     * @param submissionId 幂等键（本服务生成）
     * @param scheduleId   考试排期 id
     * @param userId       学生 id
     * @param answersJson  作答 JSON 字符串 [{questionId,userAnswer}]
     */
    public void send(String submissionId, Long scheduleId, Long userId, String answersJson) {
        String message = "{\"submissionId\":\"" + submissionId
                + "\",\"scheduleId\":" + scheduleId
                + ",\"userId\":" + userId
                + ",\"answers\":" + (answersJson == null ? "[]" : answersJson) + "}";
        try {
            kafkaTemplate.send(topic, String.valueOf(scheduleId), message)
                    .whenComplete((r, e) -> {
                        if (e != null) {
                            log.warn("考试提交消息发送失败 scheduleId={} submissionId={}: {}",
                                    scheduleId, submissionId, e.getMessage());
                        }
                    });
            log.info("考试提交消息已发送 scheduleId={} submissionId={}", scheduleId, submissionId);
        } catch (Exception e) {
            log.warn("考试提交消息发送异常 scheduleId={}: {}", scheduleId, e.getMessage());
        }
    }
}
