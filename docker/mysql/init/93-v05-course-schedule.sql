-- ============================================================
-- v1 收尾：课程排课（课表/日历数据面）
-- 排课模板：每周几 + 起止节次时间 + 位置；week_type 支持 每周/单周/双周/单次；
-- 单次或调课用 date_override 精确到日期；日历按 [日期区间] 展开为上课事件。
-- docker init 按文件名序执行；存量库手动执行一次。
-- ============================================================
USE `lms_course`;

CREATE TABLE IF NOT EXISTS `course_schedule_slot` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `course_id`     BIGINT       NOT NULL COMMENT '课程 id',
    `lesson_id`     BIGINT       NULL COMMENT '课次 id（lms-learning，可空）',
    `week_type`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1每周 2单周 3双周 4单次',
    `day_of_week`   TINYINT      NOT NULL COMMENT '星期：1(周一)..7(周日)（单次时按 date_override 起算）',
    `start_time`    TIME         NOT NULL COMMENT '开始时间',
    `end_time`      TIME         NOT NULL COMMENT '结束时间',
    `location`      VARCHAR(100) NULL COMMENT '上课地点/教室',
    `date_override` DATE         NULL COMMENT '单次/调课例外日期（week_type=4 必填；周循环场景某次调课新增单次行即可）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_course` (`course_id`),
    KEY `idx_date` (`date_override`)
) ENGINE = InnoDB COMMENT ='课程排课模板表（课表/日历展开用）';
