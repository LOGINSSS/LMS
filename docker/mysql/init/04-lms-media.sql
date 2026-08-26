-- ============================================================
-- lms_media 媒资服务数据库
-- 用途：lms-media 微服务独立数据库
-- 业务模型：文件/视频统一上传与管理，上传者关联用户（user_id）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_media` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_media`;

-- ---------- 媒资表（图片/视频/其他文件） ----------
CREATE TABLE IF NOT EXISTS `media` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '上传者（lms_user.user.id）',
    `name`        VARCHAR(255) NOT NULL COMMENT '原文件名',
    `type`        TINYINT      NOT NULL COMMENT '媒资类型：1图片 2视频 3其他',
    `url`         VARCHAR(500) NOT NULL COMMENT '访问 URL（静态映射 /uploads/**）',
    `size`        BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `mime`        VARCHAR(100) DEFAULT NULL COMMENT 'MIME 类型',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT ='媒资表';
