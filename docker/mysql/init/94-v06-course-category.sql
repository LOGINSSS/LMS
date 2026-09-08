-- ============================================================
-- v06：课程分类标签库（全局共享 + Redis 缓存）
-- 用途：课程分类从自由文本改为下拉选择全局分类；分类表作为唯一来源，
--       服务端用 Redis 缓存分类列表（lms:course:categories），新增即失效刷新。
-- 说明：docker init 首次初始化按文件名序执行；存量库手动执行一次。
-- ============================================================
USE `lms_course`;

CREATE TABLE IF NOT EXISTS `course_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称（唯一）',
    `create_by`   BIGINT       DEFAULT NULL COMMENT '创建人（教师 user id；默认种子为空）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB COMMENT ='课程分类标签表';
