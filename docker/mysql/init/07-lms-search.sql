-- ============================================================
-- lms_search 搜索服务数据库
-- 用途：lms-search 微服务独立数据库（课程主数据在 ES 索引，本库存用户兴趣标签）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_search` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_search`;

-- ---------- 用户兴趣标签表（推荐依据，权重随行为累加） ----------
CREATE TABLE IF NOT EXISTS `user_interests` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '用户（lms_user.user.id）',
    `tag`         VARCHAR(50)  NOT NULL COMMENT '兴趣标签（如课程分类：微服务/前端）',
    `weight`      INT      NOT NULL DEFAULT 1 COMMENT '兴趣权重（选课/浏览等行为累加）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag`)
) ENGINE = InnoDB COMMENT ='用户兴趣标签表';
