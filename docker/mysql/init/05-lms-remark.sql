-- ============================================================
-- lms_remark 评价互动服务数据库
-- 用途：lms-remark 微服务独立数据库
-- 业务模型：跨业务对象（课程/笔记/问答）的通用点赞互动，保留取消记录
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_remark` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_remark`;

-- ---------- 点赞记录表 ----------
CREATE TABLE IF NOT EXISTS `liked_record` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '点赞人（lms_user.user.id）',
    `biz_type`    TINYINT  NOT NULL COMMENT '点赞对象类型：1课程 2笔记 3问答',
    `biz_id`      BIGINT   NOT NULL COMMENT '点赞对象 id（课程/笔记/问答的 id）',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '点赞状态：1已赞 0已取消（保留历史）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_biz` (`user_id`, `biz_type`, `biz_id`) COMMENT '同一用户对同一对象只保留一条记录（并发兜底）',
    KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB COMMENT ='点赞记录表';
