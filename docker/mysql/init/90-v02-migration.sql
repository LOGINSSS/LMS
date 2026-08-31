-- ============================================================
-- 0.2 版本旧库迁移脚本（手动执行，针对已存在的 0.1 数据卷）
-- 库：lms_course / lms_exam / lms_learning
-- 注意：新装环境无需执行（docker/mysql/init 已含新结构）
-- ============================================================

-- ---------- lms_course：course 表加抢课字段 ----------
USE `lms_course`;
ALTER TABLE `course`
    ADD COLUMN `stock` INT NOT NULL DEFAULT 0 COMMENT '抢课总名额（0=不限）' AFTER `status`,
    ADD COLUMN `grab_start_time` DATETIME DEFAULT NULL COMMENT '抢课开始时间' AFTER `stock`,
    ADD COLUMN `grab_end_time` DATETIME DEFAULT NULL COMMENT '抢课结束时间' AFTER `grab_start_time`,
    ADD KEY `idx_grab_time` (`grab_start_time`, `grab_end_time`);

-- ---------- lms_course：课程目录（章节树） ----------
CREATE TABLE IF NOT EXISTS `course_catalog` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `course_id`   BIGINT        NOT NULL COMMENT '课程 id（lms_course.course.id）',
    `parent_id`   BIGINT        NOT NULL DEFAULT 0 COMMENT '父节点 id（0=顶级章）',
    `name`        VARCHAR(100)  NOT NULL COMMENT '章/节名称',
    `sort`        INT           NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
    `level`       TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1 章 / 2 节',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_course` (`course_id`, `deleted`)
) ENGINE = InnoDB COMMENT ='课程目录（章节树）';

-- ---------- lms_course：章节正文 ----------
CREATE TABLE IF NOT EXISTS `course_chapter` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `catalog_id`  BIGINT   NOT NULL COMMENT '目录节点 id（course_catalog.id）',
    `course_id`   BIGINT   NOT NULL COMMENT '课程 id（冗余）',
    `content_md`  LONGTEXT COMMENT '章节 markdown 正文',
    `word_count`  INT      NOT NULL DEFAULT 0 COMMENT '字数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_catalog` (`catalog_id`) COMMENT '一个目录节点只有一份正文',
    KEY `idx_course` (`course_id`)
) ENGINE = InnoDB COMMENT ='课程章节正文';

-- ---------- lms_exam：question_biz 加 biz_type ----------
USE `lms_exam`;
ALTER TABLE `question_biz`
    ADD COLUMN `biz_type` TINYINT NOT NULL DEFAULT 1 COMMENT '业务类型：1 课程 / 2 章节 / 3 考试卷' AFTER `question_id`;
-- 唯一键重建（旧 uk_question_biz 不含 biz_type）
ALTER TABLE `question_biz` DROP INDEX `uk_question_biz`;
ALTER TABLE `question_biz` ADD UNIQUE KEY `uk_question_biz` (`question_id`, `biz_type`, `biz_id`);
ALTER TABLE `question_biz` DROP INDEX `idx_biz_id`;
ALTER TABLE `question_biz` ADD KEY `idx_biz` (`biz_type`, `biz_id`);

-- ---------- lms_learning：sign_in 加 course_id ----------
USE `lms_learning`;
ALTER TABLE `sign_in`
    ADD COLUMN `course_id` BIGINT NOT NULL DEFAULT 0 COMMENT '课程 id（0=全局历史数据）' AFTER `user_id`;
-- 唯一键重建（旧 uk_user_date 不含 course_id）
ALTER TABLE `sign_in` DROP INDEX `uk_user_date`;
ALTER TABLE `sign_in` ADD UNIQUE KEY `uk_user_course_date` (`user_id`, `course_id`, `sign_date`);

-- ---------- lms_learning：points_record 加 course_id ----------
ALTER TABLE `points_record`
    ADD COLUMN `course_id` BIGINT DEFAULT NULL COMMENT '课程 id（NULL=全局流水）' AFTER `points`,
    ADD KEY `idx_course_id` (`course_id`);
