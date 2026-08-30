package com.lms.ai.task;

import com.lms.ai.config.TaskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 任务事件生产者（spec §6.2：Kafka 仅做事件通知，落库轮询为主）
 *
 * 默认不启用 Kafka（lms.ai.task.kafka-enabled=false，练手建议落库 + @Scheduled 轮询，见 spec §11）；
 * 启用后每次发布任务发一条 lms-agent-task 事件（消息含 executeTime），供外部观察/下游编排。
 * KafkaTemplate 用 ObjectProvider 注入：未配置/不可用时优雅降级为日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventProducer {

    private final TaskProperties properties;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public void publish(String taskId, LocalDateTime executeTime) {
        if (!properties.isKafkaEnabled()) {
            return;
        }
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.debug("Kafka 未配置，任务事件仅落库 taskId={}", taskId);
            return;
        }
        try {
            String message = "{\"taskId\":\"" + taskId + "\",\"executeTime\":\"" + executeTime + "\"}";
            kafkaTemplate.send(properties.getTopic(), taskId, message)
                    .whenComplete((r, e) -> {
                        if (e != null) {
                            log.warn("任务事件发送失败 taskId={}: {}", taskId, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("任务事件发送异常 taskId={}: {}", taskId, e.getMessage());
        }
    }
}
