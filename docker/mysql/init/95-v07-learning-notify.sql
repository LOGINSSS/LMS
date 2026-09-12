-- ============================================================
-- v07: learning notification (mailbox) - QA notify inbox
-- purpose: student asks question -> notify course teacher (unread);
--          teacher answers -> notify the student who asked.
-- note: docker mysql init executes files in name order; for an
--       already-initialized database run this script manually once
--       (idempotent via CREATE TABLE IF NOT EXISTS).
-- ============================================================
USE `lms_learning`;

CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    `user_id`     BIGINT       NOT NULL COMMENT 'recipient user id (lms_user.user.id)',
    `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=teacher pending; 2=student answered; 3=teacher resolved',
    `course_id`   BIGINT       NULL COMMENT 'related course id',
    `question_id` BIGINT       NULL COMMENT 'related qa question id',
    `title`       VARCHAR(255) NULL COMMENT 'short title for inbox row',
    `content`     VARCHAR(512) NULL COMMENT 'extra context / answer preview',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=unread 1=read',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`, `is_read`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'in-app mailbox notifications (QA answer flow)';
