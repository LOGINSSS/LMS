-- Existing lms_ai databases must apply this additive migration once.
USE `lms_ai`;

CREATE TABLE IF NOT EXISTS `agent_memory_outbox` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `event_id`          VARCHAR(32)  NOT NULL COMMENT '投递事件幂等标识',
    `user_id`           BIGINT       NOT NULL COMMENT '归属用户；只用于路由个人 workspace',
    `session_id`        BIGINT       NOT NULL COMMENT '来源会话；每个会话只生成一个投递任务',
    `payload`           JSON         NOT NULL COMMENT '已裁剪的 ReMe auto_memory 请求',
    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '0待执行 1处理中 2完成 3重试 4死信',
    `attempts`          INT          NOT NULL DEFAULT 0 COMMENT '已领取执行次数',
    `next_attempt_time` DATETIME     NOT NULL COMMENT '下次执行时间；处理中时表示租约到期时间',
    `finish_time`       DATETIME     NULL COMMENT '完成或死信时间',
    `last_error_type`   VARCHAR(128) NULL COMMENT '最后错误类型；不存错误消息以免泄露敏感数据',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_memory_event` (`event_id`),
    UNIQUE KEY `uk_memory_session` (`session_id`),
    KEY `idx_memory_due` (`status`, `next_attempt_time`)
) ENGINE = InnoDB COMMENT ='ReMe 长期记忆可靠投递 Outbox';
