-- ============================================================
-- v1 收尾：选课资格约束（课程发布时框定可选用户范围）
-- 规则声明式 JSON（course_enroll_rule.rule_json）：
--   {"mode":"ALL","rules":[{"type":"grade","op":"IN","values":["大三"]},
--                          {"type":"course_done","courseId":12},
--                          {"type":"points_min","value":100}]}
-- 判定由 lms-course eligibility 服务逐条解析（多源查询拼接），非 SQL 直查。
-- docker init 首次初始化按文件名序执行；存量库手动执行一次。
-- ============================================================
USE `lms_course`;

CREATE TABLE IF NOT EXISTS `course_enroll_rule` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `course_id`   BIGINT       NOT NULL COMMENT '课程 id',
    `rule_json`   TEXT         NULL COMMENT '规则 JSON（mode+rules，见文件头）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course` (`course_id`) COMMENT '一课程一条规则（无=不限）'
) ENGINE = InnoDB COMMENT ='选课资格约束规则表';
