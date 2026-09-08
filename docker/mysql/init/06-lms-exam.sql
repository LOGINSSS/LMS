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

-- ---------- 发布物排期表（v1 收尾 P2b-③/④：老师排期发布【作业 biz_type=2 | 考试 biz_type=1】，
--             挂已发布卷面 paper_id；学生侧"我的考试/作业日历"按报名课程展示；提交在服务端按卷面快照判分） ----------
CREATE TABLE IF NOT EXISTS `exam_schedule` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `title`            VARCHAR(255) NOT NULL COMMENT '发布物标题（考试/作业名）',
    `description`      VARCHAR(1000) DEFAULT NULL COMMENT '说明',
    `biz_type`         TINYINT      NOT NULL DEFAULT 1 COMMENT '发布物类型：1 考试 2 作业',
    `paper_id`         BIGINT       NOT NULL COMMENT '挂载卷面 id（exam_paper，已发布）',
    `course_ids`       VARCHAR(500) NOT NULL COMMENT '适用课程 id 集合（逗号分隔，可多课程）',
    `teacher_id`       BIGINT       NOT NULL COMMENT '发布老师 id',
    `duration_minutes` INT          NOT NULL DEFAULT 90 COMMENT '作答时长（分钟）',
    `start_time`       DATETIME     NOT NULL COMMENT '开始时间（作业=可开始作答时间；考试=开考）',
    `end_time`         DATETIME     NOT NULL COMMENT '截止时间（作业=提交截止；考试=考试结束）',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1已发布 2已结束（0草稿预留）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_teacher` (`teacher_id`, `biz_type`, `status`),
    KEY `idx_time` (`start_time`, `end_time`),
    KEY `idx_paper` (`paper_id`)
) ENGINE = InnoDB COMMENT ='发布物排期表（考试/作业）';

-- ---------- 试卷快照表（v1 收尾：老师出卷流程骨干，作业/考试 rails 共用；
--             组卷时把题库题目"快照"进卷，学生按卷答题只看题干（去答案渲染），判分在服务端确定性完成） ----------
CREATE TABLE IF NOT EXISTS `exam_paper` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(255) NOT NULL COMMENT '卷面标题',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '卷面说明',
    `course_id`   BIGINT       DEFAULT NULL COMMENT '关联课程 id（作业/考试场景）',
    `teacher_id`  BIGINT       NOT NULL COMMENT '组卷老师 id',
    `total_score` INT          NOT NULL DEFAULT 0 COMMENT '卷面总分（发布时汇总）',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布（2停用预留）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_teacher` (`teacher_id`, `status`)
) ENGINE = InnoDB COMMENT ='试卷快照表（出卷流程产物）';

CREATE TABLE IF NOT EXISTS `exam_paper_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `paper_id`     BIGINT       NOT NULL COMMENT '试卷 id',
    `seq`          INT          NOT NULL COMMENT '卷内序号（1 递增）',
    `question_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '来源题库题目 id（0=非题库快照题，预留 AI 自测/KB 题）',
    `stem`         VARCHAR(1000) NOT NULL COMMENT '题干（含选项文本快照）',
    `type`         TINYINT      NOT NULL COMMENT '题型：1单选 2多选 3判断',
    `category`     VARCHAR(50)  DEFAULT NULL COMMENT '知识点/分类快照',
    `difficulty`   TINYINT      NOT NULL DEFAULT 1 COMMENT '难度：1易 2中 3难',
    `score`        INT          NOT NULL DEFAULT 1 COMMENT '该题分值',
    `answer`       VARCHAR(500) NOT NULL COMMENT '答案快照（JSON，服务端判分用，绝不下发学生）',
    `analysis`     VARCHAR(500) DEFAULT NULL COMMENT '解析快照（讲解用）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_seq` (`paper_id`, `seq`) COMMENT '卷内序号唯一',
    KEY `idx_paper` (`paper_id`)
) ENGINE = InnoDB COMMENT ='试卷快照明细表';

-- ---------- 考试提交表（前端轨：考试专用锁页 → Kafka 异步提交（topic lms-exam-submission）→ 本表幂等落库 → 判分回流）
--             幂等：submission_id 全局唯一（提交页生成，消费端 DuplicateKey 兜底，消息不丢不重） ----------
CREATE TABLE IF NOT EXISTS `exam_submission` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `submission_id` VARCHAR(64)  NOT NULL COMMENT '提交幂等键（前端/客户端生成 UUID）',
    `schedule_id`   BIGINT       NOT NULL COMMENT '考试排期 id',
    `user_id`       BIGINT       NOT NULL COMMENT '作答学生 id',
    `paper_id`      BIGINT       NOT NULL COMMENT '卷面 id（排期挂载）',
    `answers`       TEXT         NULL COMMENT '作答 JSON：[{questionId,userAnswer}]',
    `correct_count` INT          NOT NULL DEFAULT 0 COMMENT '答对题数',
    `score`         INT          NOT NULL DEFAULT 0 COMMENT '得分',
    `total_score`   INT          NOT NULL DEFAULT 0 COMMENT '卷面总分',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0处理中 1完成 2失败',
    `error_msg`     VARCHAR(500) NULL COMMENT '失败原因',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_submission` (`submission_id`) COMMENT '幂等键',
    KEY `idx_schedule_user` (`schedule_id`, `user_id`)
) ENGINE = InnoDB COMMENT ='考试提交表（Kafka 异步幂等落库）';
