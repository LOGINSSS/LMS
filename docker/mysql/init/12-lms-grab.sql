-- ============================================================
-- lms_grab 抢课服务数据库（0.2 新增模块）
-- 用途：抢课记录（Redis 预检库存 + Kafka 异步落库的落库侧审计）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_grab` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_grab`;

-- ---------- 抢课记录表（抢课成功待落库 → 已落库 / 已回补） ----------
CREATE TABLE IF NOT EXISTS `grab_record` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `course_id`   BIGINT   NOT NULL COMMENT '课程 id',
    `user_id`     BIGINT   NOT NULL COMMENT '抢课用户（lms_user.user.id）',
    `grab_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抢课时间',
    `source`      TINYINT  NOT NULL DEFAULT 1 COMMENT '来源：1 抢课',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 成功待落库 / 2 已落库 / 3 已回补',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_user` (`course_id`, `user_id`) COMMENT '同一用户同一课程只允许一条抢课记录（并发兜底）',
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT ='抢课记录表';
