-- ============================================================
-- v1 收尾 P5：agent_task 会话级任务板扩展（HEAVY_HARNESS_SPEC §5.3）
-- 用途：给 agent_task 挂会话/父子任务/意图/动作维度，支撑 turn 落板与任务树查询。
-- 说明：docker mysql init 首次初始化时按文件名序执行本文件（在 11-lms-ai.sql 之后）；
--       对已初始化的存量库需手动执行一次本脚本（幂等性由列不存在保证，重复执行会报重复列，忽略即可）。
-- ============================================================
USE `lms_ai`;

ALTER TABLE `agent_task`
    ADD COLUMN `session_id`     BIGINT       NULL COMMENT '归属会话 id（turn/子任务关联）' AFTER `owner_id`,
    ADD COLUMN `parent_task_id` BIGINT       NULL COMMENT '父任务 id（任务树）' AFTER `session_id`,
    ADD COLUMN `intent`         VARCHAR(32)  NULL COMMENT '触发意图（L0 输出）' AFTER `parent_task_id`,
    ADD COLUMN `action_type`    VARCHAR(16)  NULL COMMENT '动作类型：turn/invite/tool/pipeline/timer' AFTER `intent`,
    ADD COLUMN `action_ref`     VARCHAR(64)  NULL COMMENT '动作引用：invite:exam-agent / tool:exam.saveQuestion' AFTER `action_type`,
    ADD COLUMN `depth`          INT          NOT NULL DEFAULT 0 COMMENT '任务树深度' AFTER `action_ref`;

CREATE INDEX `idx_agent_task_session` ON `agent_task` (`session_id`, `status`);
CREATE INDEX `idx_agent_task_parent` ON `agent_task` (`parent_task_id`);
