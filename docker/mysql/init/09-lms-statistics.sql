-- ============================================================
-- lms_statistics 数据中心服务数据库
-- 用途：lms-statistics 微服务独立数据库
-- 业务模型：每日统计快照（跨域数据经 Feign 实时聚合后落库）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_statistics` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_statistics`;

-- ---------- 每日统计快照表（数据中心看板落库） ----------
CREATE TABLE IF NOT EXISTS `daily_stats` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT,
    `stat_date`     DATE     NOT NULL COMMENT '统计日期',
    `user_count`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户总数',
    `course_count`  BIGINT   NOT NULL DEFAULT 0 COMMENT '课程总数（已发布）',
    `enroll_count`  BIGINT   NOT NULL DEFAULT 0 COMMENT '选课人次',
    `learn_count`   BIGINT   NOT NULL DEFAULT 0 COMMENT '学习人次累计',
    `today_user`    BIGINT   NOT NULL DEFAULT 0 COMMENT '今日新增用户',
    `today_course`  BIGINT   NOT NULL DEFAULT 0 COMMENT '今日新增课程',
    `today_sign`    BIGINT   NOT NULL DEFAULT 0 COMMENT '今日签到人数',
    `today_learn`   BIGINT   NOT NULL DEFAULT 0 COMMENT '今日学习人次',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_date` (`stat_date`) COMMENT '每天一条快照'
) ENGINE = InnoDB COMMENT ='每日统计快照表';
