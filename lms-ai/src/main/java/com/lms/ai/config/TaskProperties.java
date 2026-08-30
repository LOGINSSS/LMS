package com.lms.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务与事件配置（前缀 lms.ai.task，spec §6：复用 Kafka 事件 + 落库轮询）
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.task")
public class TaskProperties {

    /** 定时任务开关（阶段 4） */
    private boolean enabled = true;

    /** 任务事件 Kafka topic */
    private String topic = "lms-agent-task";

    /** 行为事件 Kafka topic（spec §4.3：行为 → 画像） */
    private String behaviorTopic = "lms-agent-behavior";

    /** 学生问答 → 老师 2 小时未回复提醒（秒） */
    private long qaRemindDelaySeconds = 7200;

    /** 是否启用 Kafka 事件通知（默认 false：练手用落库 + @Scheduled 轮询，spec §11） */
    private boolean kafkaEnabled = false;
}
