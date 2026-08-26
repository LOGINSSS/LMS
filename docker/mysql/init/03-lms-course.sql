-- ============================================================
-- lms_course 课程服务数据库
-- 用途：lms-course 微服务独立数据库
-- 业务模型：一个课程只归属一个教师（course.teacher_id）；
--           一个课程可被多个学生选课（course_enrollment，一对多）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `lms_course` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `lms_course`;

-- ---------- 课程主表（教师创建，冗余教师昵称用于卡片展示免联表） ----------
CREATE TABLE IF NOT EXISTS `course` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `teacher_id`  BIGINT        NOT NULL COMMENT '归属教师（lms_user.user.id，一个课程只属于一个教师）',
    `teacher_name` VARCHAR(50)  DEFAULT NULL COMMENT '教师昵称快照（创建时冗余，卡片展示免跨服务查询）',
    `name`        VARCHAR(100)  NOT NULL COMMENT '课程名称',
    `cover`       VARCHAR(255)  DEFAULT NULL COMMENT '封面图 URL',
    `intro`       VARCHAR(500)  DEFAULT NULL COMMENT '课程简介（卡片展示用）',
    `category`    VARCHAR(50)   DEFAULT NULL COMMENT '课程分类（如 微服务/前端/数据库）',
    `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '价格（元），0 表示免费',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0 已下架 / 1 已发布（卡片列表只展示已发布）',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT ='课程表';

-- ---------- 选课关系表（一个课程接受很多学生，一对多） ----------
CREATE TABLE IF NOT EXISTS `course_enrollment` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `course_id`   BIGINT   NOT NULL COMMENT '课程 id',
    `student_id`  BIGINT   NOT NULL COMMENT '选课学生（lms_user.user.id）',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '选课状态：1 选课中 / 0 已退课',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_student` (`course_id`, `student_id`) COMMENT '同一学生同一课程只允许一条选课记录（并发兜底）',
    KEY `idx_student_id` (`student_id`)
) ENGINE = InnoDB COMMENT ='选课关系表';
