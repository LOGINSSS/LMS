-- ============================================================
-- lms_learning 学习过程服务数据库
-- 用途：lms-learning 微服务独立数据库
-- 业务模型：课次、学习记录、笔记、互动问答、积分、签到
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_learning` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_learning`;

-- ---------- 课次表（课程下的学习单元，教师创建） ----------
CREATE TABLE IF NOT EXISTS `lesson` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `course_id`   BIGINT       NOT NULL COMMENT '所属课程 id',
    `name`        VARCHAR(100) NOT NULL COMMENT '课次名称',
    `media_id`    BIGINT       DEFAULT NULL COMMENT '关联媒资视频 id（lms_media.media.id）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '课次排序（小在前）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`)
) ENGINE = InnoDB COMMENT ='课次表';

-- ---------- 学习记录表（用户对课次的学习进度） ----------
CREATE TABLE IF NOT EXISTS `learning_record` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT   NOT NULL COMMENT '学习者（lms_user.user.id）',
    `course_id`       BIGINT   NOT NULL COMMENT '课程 id',
    `lesson_id`       BIGINT   NOT NULL COMMENT '课次 id',
    `progress`        INT      NOT NULL DEFAULT 0 COMMENT '学习进度百分比（0-100）',
    `last_learn_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近学习时间',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_lesson` (`user_id`, `lesson_id`) COMMENT '同一用户同一课次一条记录',
    KEY `idx_course_id` (`course_id`)
) ENGINE = InnoDB COMMENT ='学习记录表';

-- ---------- 笔记表（用户对课次/课程的学习笔记） ----------
CREATE TABLE IF NOT EXISTS `note` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '笔记作者',
    `course_id`   BIGINT       NOT NULL COMMENT '课程 id',
    `lesson_id`   BIGINT       DEFAULT NULL COMMENT '课次 id（可空，课程级笔记）',
    `content`     VARCHAR(2000) NOT NULL COMMENT '笔记内容',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT ='笔记表';

-- ---------- 互动问答-问题表 ----------
CREATE TABLE IF NOT EXISTS `question` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '提问人',
    `course_id`   BIGINT       NOT NULL COMMENT '所属课程 id',
    `title`       VARCHAR(200) NOT NULL COMMENT '问题标题',
    `content`     VARCHAR(2000) DEFAULT NULL COMMENT '问题详情',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`)
) ENGINE = InnoDB COMMENT ='互动问答问题表';

-- ---------- 互动问答-回答表 ----------
CREATE TABLE IF NOT EXISTS `answer` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `question_id` BIGINT       NOT NULL COMMENT '问题 id',
    `user_id`     BIGINT       NOT NULL COMMENT '回答人',
    `content`     VARCHAR(2000) NOT NULL COMMENT '回答内容',
    `accepted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否被采纳：1是 0否',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE = InnoDB COMMENT ='互动问答回答表';

-- ---------- 积分记录表（签到/学习/问答等行为积分流水） ----------
CREATE TABLE IF NOT EXISTS `points_record` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '用户',
    `type`        TINYINT  NOT NULL COMMENT '积分类型：1签到 2学习 3提问 4回答 5回答被采纳 6阅读章节 7考试',
    `points`      INT      NOT NULL COMMENT '积分变动（正增负减）',
    `course_id`   BIGINT   DEFAULT NULL COMMENT '课程 id（NULL=全局流水；非空=课程内积分，计入课程积分榜 ZSET）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB COMMENT ='积分记录表';

-- ---------- 签到表（课程页每日一次，0.2 改为课程维度） ----------
CREATE TABLE IF NOT EXISTS `sign_in` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '签到用户',
    `course_id`   BIGINT   NOT NULL DEFAULT 0 COMMENT '课程 id（0=全局历史数据）',
    `sign_date`   DATE     NOT NULL COMMENT '签到日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_course_date` (`user_id`, `course_id`, `sign_date`) COMMENT '同一用户同一课程每天一次（并发兜底）'
) ENGINE = InnoDB COMMENT ='签到表';

-- ============================================================
-- 学习数据中心扩展（评测业务线：诊断→规划→习题→测评闭环，需求文档 §4.7）
-- ============================================================

-- ---------- 做题记录表（错题集原料：习题/测评作答明细） ----------
CREATE TABLE IF NOT EXISTS `exercise_record` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT        NOT NULL COMMENT '做题学生',
    `course_id`        BIGINT        NOT NULL COMMENT '课程 id',
    `question_id`      BIGINT        NOT NULL COMMENT '题目 id（lms_exam.question.id，可为 0 表示测评题）',
    `question_type`    TINYINT       DEFAULT NULL COMMENT '题型：1单选 2多选 3判断',
    `knowledge_point`  VARCHAR(64)   DEFAULT NULL COMMENT '对应知识点（题目 category）',
    `user_answer`      VARCHAR(1000) DEFAULT NULL COMMENT '学生作答（JSON）',
    `correct`          TINYINT       NOT NULL DEFAULT 0 COMMENT '是否答对：1对 0错',
    `score`            INT           NOT NULL DEFAULT 0 COMMENT '本题得分',
    `source`           TINYINT       NOT NULL DEFAULT 1 COMMENT '来源：1练习 2测评',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_user_course` (`user_id`, `course_id`, `create_time`),
    KEY `idx_knowledge` (`user_id`, `knowledge_point`)
) ENGINE = InnoDB COMMENT ='做题记录表（错题集原料）';

-- ---------- 测评结果表（学习效果评估报告留存，需求文档 §4.5/§6.4 可观测性） ----------
CREATE TABLE IF NOT EXISTS `learning_assessment` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT       NOT NULL COMMENT '被测评学生',
    `course_id`         BIGINT       NOT NULL COMMENT '课程 id',
    `title`             VARCHAR(100) DEFAULT NULL COMMENT '测评标题（如：数据结构-图 单元测评）',
    `report`            TEXT         NULL COMMENT '评估报告（智能体输出，markdown/文本）',
    `total_score`       INT          NOT NULL DEFAULT 0 COMMENT '总分',
    `knowledge_mastery` TEXT         NULL COMMENT '知识点掌握度 JSON（{知识点: 掌握度0-100}）',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_user_course` (`user_id`, `course_id`)
) ENGINE = InnoDB COMMENT ='测评结果表（学习效果评估报告）';
