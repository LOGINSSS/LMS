package com.lms.course.course.mq;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes an acknowledgement after course enrollment has landed. */
@Component
@RequiredArgsConstructor
public class GrabLandedProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${lms.grab.landed-topic:lms-grab-landed}")
    private String topic;

    public void publish(Long recordId, Long courseId, Long userId) {
        String payload = JSONUtil.createObj()
                .set("recordId", recordId)
                .set("courseId", courseId)
                .set("userId", userId)
                .toString();
        kafkaTemplate.send(topic, String.valueOf(recordId), payload);
    }
}
