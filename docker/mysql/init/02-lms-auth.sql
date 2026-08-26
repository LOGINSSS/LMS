-- ============================================================
-- lms_auth 认证服务数据库（登录账号 + 登录日志）
-- 用途：lms-auth 微服务独立数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_auth` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_auth`;

-- ---------- 登录账号（凭据；档案在 lms_user.user） ----------
CREATE TABLE IF NOT EXISTS `account` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录账号（手机号/邮箱，全局唯一）',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密）',
    `user_type`   TINYINT      NOT NULL COMMENT '用户类型：1学生 2教师',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '关联 lms_user.user.id（注册成功后回填）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB COMMENT ='登录账号表';

-- ---------- 登录日志 ----------
CREATE TABLE IF NOT EXISTS `login_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `account_id`  BIGINT       NOT NULL COMMENT '账号id',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '用户档案id（登录成功时回填）',
    `user_type`   TINYINT      NOT NULL COMMENT '用户类型',
    `login_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `ip`          VARCHAR(50)  DEFAULT NULL COMMENT '登录IP',
    `device`      VARCHAR(200) DEFAULT NULL COMMENT '设备/UA',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '登录结果：1成功 0失败',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB COMMENT ='登录日志表';
