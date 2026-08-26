-- ============================================================
-- lms_user 用户服务数据库（学生/教师档案）
-- 用途：lms-user 微服务独立数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_user`;

-- ---------- 用户档案主表（公共字段；凭据在 lms_auth.account） ----------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户档案id',
    `account_id`  BIGINT       NOT NULL COMMENT '关联 lms_auth.account.id',
    `user_type`   TINYINT      NOT NULL COMMENT '用户类型：1学生 2教师',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_id` (`account_id`),
    KEY `idx_user_type` (`user_type`)
) ENGINE = InnoDB COMMENT ='用户档案表';

-- ---------- 教师扩展信息 ----------
CREATE TABLE IF NOT EXISTS `teacher_info` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '关联 user.id',
    `college`     VARCHAR(100) DEFAULT NULL COMMENT '院系',
    `title`       VARCHAR(50)  DEFAULT NULL COMMENT '职称',
    `bio`         VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT ='教师扩展信息表';

-- ---------- 学生扩展信息 ----------
CREATE TABLE IF NOT EXISTS `student_info` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '关联 user.id',
    `student_no`  VARCHAR(30)  DEFAULT NULL COMMENT '学号',
    `major`       VARCHAR(100) DEFAULT NULL COMMENT '专业',
    `grade`       VARCHAR(20)  DEFAULT NULL COMMENT '年级',
    `class_name`  VARCHAR(50)  DEFAULT NULL COMMENT '班级',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT ='学生扩展信息表';
