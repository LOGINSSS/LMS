-- ============================================================
-- lms_exam 考试/题库服务数据库
-- 用途：lms-exam 微服务独立数据库
-- 业务模型：题目主数据 + 题目与业务（课程/考试）的绑定关系
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_exam` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_exam`;

-- ---------- 题目表 ----------
CREATE TABLE IF NOT EXISTS `question` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(255) NOT NULL COMMENT '题干',
    `type`        TINYINT      NOT NULL COMMENT '题型：1单选 2多选 3判断',
    `category`    VARCHAR(50)  DEFAULT NULL COMMENT '题目分类（如 微服务/Java/数据库）',
    `difficulty`  TINYINT      NOT NULL DEFAULT 1 COMMENT '难度：1易 2中 3难',
    `analysis`    VARCHAR(500) DEFAULT NULL COMMENT '答案解析',
    `answer`      VARCHAR(500) DEFAULT NULL COMMENT '答案（JSON，如 {"option":"A"} / {"options":["A","B"]} / {"judge":true}）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_type_category` (`type`, `category`)
) ENGINE = InnoDB COMMENT ='题目表';

-- ---------- 题目-业务绑定表（题目归属哪个课程/章节/考试卷，含分值） ----------
CREATE TABLE IF NOT EXISTS `question_biz` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `question_id` BIGINT   NOT NULL COMMENT '题目 id',
    `biz_type`    TINYINT  NOT NULL DEFAULT 1 COMMENT '业务类型：1 课程 / 2 章节 / 3 考试卷',
    `biz_id`      BIGINT   NOT NULL COMMENT '业务 id（biz_type=1 为课程 id；2 为章节目录 id；3 为考试卷 id）',
    `score`       INT      NOT NULL DEFAULT 0 COMMENT '该业务下题目分值',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_biz` (`question_id`, `biz_type`, `biz_id`) COMMENT '同一题目在同一业务类型下唯一',
    KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB COMMENT ='题目-业务绑定表';
