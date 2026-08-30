-- ============================================================
-- lms_kb 知识库服务数据库
-- 用途：lms-kb 微服务独立数据库。知识库归属 owner_type + owner_id
--       （spec §4.4）：课程库 owner_type=1 + course_id（旧语义保留）；
--       个人库 owner_type=2 + owner_id=userId。文档切片(chunk)存 ES
--       （lms_kb_chunk 索引），本库存元数据与评估样本。
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_kb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_kb`;

-- ---------- 知识库表（归属 owner_type + owner_id，uk(owner_type, owner_id)） ----------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_type`  TINYINT      NOT NULL DEFAULT 1 COMMENT '归属类型：1 课程 / 2 用户（个人知识库）',
    `owner_id`    BIGINT       NULL COMMENT '归属 id：owner_type=1 时为课程 id；owner_type=2 时为用户 id',
    `course_id`   BIGINT       NULL COMMENT '课程 id（owner_type=1 时冗余，兼容旧数据/旧接口）',
    `name`        VARCHAR(100) NOT NULL COMMENT '知识库名称（默认取课程名）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0 禁用 / 1 可用',
    `doc_count`   INT          NOT NULL DEFAULT 0 COMMENT '文档数量（含处理中）',
    `chunk_count` INT          NOT NULL DEFAULT 0 COMMENT '已入库切片总数',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner` (`owner_type`, `owner_id`),
    KEY `idx_course_id` (`course_id`)
) ENGINE = InnoDB COMMENT ='知识库表（课程库/个人库）';

-- ---------- 知识库文档表 ----------
CREATE TABLE IF NOT EXISTS `knowledge_doc` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `kb_id`       BIGINT        NOT NULL COMMENT '知识库 id（knowledge_base.id）',
    `owner_type`  TINYINT       NOT NULL DEFAULT 1 COMMENT '归属类型：1 课程 / 2 用户（冗余自知识库）',
    `owner_id`    BIGINT        NULL COMMENT '归属 id（冗余自知识库）',
    `course_id`   BIGINT        NULL COMMENT '课程 id（owner_type=1 时冗余，检索过滤用）',
    `file_name`   VARCHAR(255)  NOT NULL COMMENT '展示用文件名（含扩展名）',
    `file_type`   VARCHAR(20)   NOT NULL COMMENT '文件类型：md/txt/docx/pptx/pdf/png/jpg/...',
    `file_id`     BIGINT        NULL COMMENT 'lms-media 文件 id（若走媒资服务）',
    `chunk_count` INT           NOT NULL DEFAULT 0 COMMENT '入库切片数',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '处理状态：0待解析 1解析中 2向量化中 3完成 4失败',
    `error_msg`   VARCHAR(500)  NULL COMMENT '失败原因',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_owner` (`owner_type`, `owner_id`)
) ENGINE = InnoDB COMMENT ='知识库文档表';

-- ---------- RAGAS 评估样本表（RAG 链路中间产物 + 结果快照） ----------
CREATE TABLE IF NOT EXISTS `rag_eval_sample` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `course_id`    BIGINT       NULL COMMENT '课程 id（个人知识库为 NULL）',
    `question`     TEXT         NOT NULL COMMENT '用户问题',
    `rewritten`    TEXT         NULL COMMENT 'rewrite 节点改写后的查询',
    `hyde`         TEXT         NULL COMMENT 'HyDE 节点假设性回答',
    `contexts`     MEDIUMTEXT   NULL COMMENT '精排后上下文 JSON（[{text,source,doc_type,score}]）',
    `answer`       TEXT         NULL COMMENT '生成答案',
    `ground_truth` TEXT         NULL COMMENT '标准答案（人工标注，评估用）',
    `sources`      MEDIUMTEXT   NULL COMMENT '答案来源 JSON（[{source,doc_type,text}]）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`)
) ENGINE = InnoDB COMMENT ='RAGAS 评估样本表（RAG 结构化输出快照）';

-- ============================================================
-- 兼容迁移（旧库升级，spec §4.4：课程库语义保留）
-- 对已存在的 knowledge_base / knowledge_doc 表执行：
--   1. 加 owner_type / owner_id 列
--   2. 旧数据回填：owner_type=1, owner_id=course_id（课程库）
--   3. 唯一约束从 uk_course(course_id) 调整为 uk_owner(owner_type, owner_id)
-- ============================================================
SET @has_owner_type := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'lms_kb' AND TABLE_NAME = 'knowledge_base' AND COLUMN_NAME = 'owner_type');
SET @sql := IF(@has_owner_type = 0,
    'ALTER TABLE `knowledge_base`
        ADD COLUMN `owner_type` TINYINT NOT NULL DEFAULT 1 COMMENT ''归属类型：1 课程 / 2 用户'' AFTER `id`,
        ADD COLUMN `owner_id` BIGINT NULL COMMENT ''归属 id'' AFTER `owner_type`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `knowledge_base` SET `owner_type` = 1, `owner_id` = `course_id` WHERE `owner_id` IS NULL;

-- 调整唯一约束（删除旧的 uk_course，若存在）
SET @has_uk_course := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'lms_kb' AND TABLE_NAME = 'knowledge_base' AND INDEX_NAME = 'uk_course');
SET @sql := IF(@has_uk_course > 0,
    'ALTER TABLE `knowledge_base` DROP INDEX `uk_course`, ADD UNIQUE KEY `uk_owner` (`owner_type`, `owner_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- knowledge_doc 加 owner 列（旧库）
SET @has_doc_owner := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'lms_kb' AND TABLE_NAME = 'knowledge_doc' AND COLUMN_NAME = 'owner_type');
SET @sql := IF(@has_doc_owner = 0,
    'ALTER TABLE `knowledge_doc`
        ADD COLUMN `owner_type` TINYINT NOT NULL DEFAULT 1 COMMENT ''归属类型：1 课程 / 2 用户'' AFTER `kb_id`,
        ADD COLUMN `owner_id` BIGINT NULL COMMENT ''归属 id'' AFTER `owner_type`,
        ADD KEY `idx_owner` (`owner_type`, `owner_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `knowledge_doc` SET `owner_type` = 1, `owner_id` = `course_id` WHERE `owner_id` IS NULL;

-- rag_eval_sample.course_id 改为可空（个人知识库检索无课程 id）
SET @has_eval_not_null := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'lms_kb' AND TABLE_NAME = 'rag_eval_sample' AND COLUMN_NAME = 'course_id');
SET @sql := IF(@has_eval_not_null = 'NO',
    'ALTER TABLE `rag_eval_sample` MODIFY COLUMN `course_id` BIGINT NULL COMMENT ''课程 id（个人知识库为 NULL）''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
