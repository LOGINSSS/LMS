-- ============================================================
-- lms_ai 个人 Agent 数据库（spec §4.2/§4.3/§6.1）
-- 用途：lms-ai 微服务独立数据库。L1 会话元数据、L2 画像/行为流水、
--       画像生成任务、定时任务表。L1 消息本体存 Redis（agent:session:{id}:messages）。
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_ai` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_ai`;

-- ---------- L1 会话表（元数据；消息本体在 Redis，spec §4.2） ----------
CREATE TABLE IF NOT EXISTS `agent_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL COMMENT '归属用户（lms-auth 用户 id）',
    `agent_type`    VARCHAR(32)  NOT NULL COMMENT 'agent 类型：student-agent / teacher-agent',
    `title`         VARCHAR(100) NOT NULL DEFAULT '新会话' COMMENT '会话标题（默认取首条消息前 N 字）',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 进行中 / 0 已结束',
    `message_count` INT          NOT NULL DEFAULT 0 COMMENT '消息条数（冗余统计）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `status`)
) ENGINE = InnoDB COMMENT ='Agent 会话表（L1 元数据）';

-- ---------- L2 用户画像主表（一人一行，agent 眼中的用户，spec §4.3） ----------
CREATE TABLE IF NOT EXISTS `agent_user_profile` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT        NOT NULL COMMENT 'lms-auth 用户 id',
    `role`            TINYINT       NOT NULL DEFAULT 1 COMMENT '角色：1 学生 / 2 老师',
    `display_name`    VARCHAR(64)   NULL COMMENT '昵称',
    `summary`         TEXT          NULL COMMENT '画像摘要：agent 定期压缩生成（习惯/偏好/近期目标）',
    `interests`       VARCHAR(512)  NULL COMMENT '兴趣标签（可同步 lms-search user_interests）',
    `learning_habits` TEXT          NULL COMMENT '学习习惯 JSON：活跃时段/学习节奏/擅长薄弱知识点',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='用户画像主表（L2）';

-- ---------- L2 行为事件流水（画像原料，spec §4.3） ----------
CREATE TABLE IF NOT EXISTS `agent_user_behavior` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '用户 id',
    `event_type`  VARCHAR(32)  NOT NULL COMMENT '事件类型：chat/ask_question/answer/quiz/sign_in/search/like/kb_upload...',
    `payload`     JSON         NULL COMMENT '事件明细（问题主题/题目知识点/搜索词...）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB COMMENT ='行为事件流水（L2 原料）';

-- ---------- L2 画像生成任务（摘要的异步计算，spec §4.3） ----------
CREATE TABLE IF NOT EXISTS `agent_profile_job` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT   NOT NULL COMMENT '用户 id',
    `status`            TINYINT  NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2完成 3失败',
    `last_summary_time` DATETIME NULL COMMENT '上次摘要时间',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB COMMENT ='画像生成任务（L2 摘要异步计算）';

-- ---------- 定时任务表（spec §6.1） ----------
CREATE TABLE IF NOT EXISTS `agent_task` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`       VARCHAR(64)  NOT NULL COMMENT '业务幂等键（UUID）',
    `owner_id`      BIGINT       NOT NULL COMMENT '归属用户（老师/学生）',
    `agent_name`    VARCHAR(64)  NULL COMMENT '发起 agent',
    `task_type`     VARCHAR(32)  NOT NULL COMMENT '任务类型：qa_remind / outline_generate / report_xxx ...',
    `payload`       JSON         NULL COMMENT '任务参数（问题、课程、回复地址...）',
    `trigger_type`  TINYINT      NOT NULL COMMENT '1延迟执行 2周期执行 3一次性',
    `delay_seconds` INT          NULL COMMENT '延迟秒数（trigger_type=1）',
    `cron`          VARCHAR(32)  NULL COMMENT '周期 cron（trigger_type=2）',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2完成 3失败 4取消',
    `result`        TEXT         NULL COMMENT '执行结果（agent 输出）',
    `error_msg`     VARCHAR(500) NULL COMMENT '失败原因',
    `execute_time`  DATETIME     NULL COMMENT '计划执行时间',
    `finish_time`   DATETIME     NULL COMMENT '完成时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_status_time` (`status`, `execute_time`)
) ENGINE = InnoDB COMMENT ='Agent 定时任务表';
