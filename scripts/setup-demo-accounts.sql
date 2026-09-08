-- ============================================================
-- 演示环境重置 + 双演示账号脚本（手动执行，一次性）
--
-- 用途：
--   1. 全量清空各业务库的演示/测试数据（课程、选课、学习、题库、
--      媒资、点赞、搜索标签、知识库、AI、抢课、统计、登录日志）；
--   2. 删除全部旧测试账号及档案（stu001/tea001/cn_stu/tea002/
--      web_stu/v02teacher/v02student 等）；
--   3. 重建两个演示账号：t_demo（教师）、s_demo（学生），
--      密码均为 123456（BCrypt 加密存储）。
--
-- 执行方式（连 lms-mysql，即宿主 13306）：
--   mysql -h127.0.0.1 -P13306 -uroot -proot --default-character-set=utf8mb4
--         -e "SOURCE D:/path/to/scripts/setup-demo-accounts.sql"
--
-- 注意：破坏性脚本，清空后不可恢复；执行前请确认无需保留当前数据。
--       执行后建议清空 ES course 索引与 Redis 缓存（见 README 注释）。
-- ============================================================

-- ---------- 1. 清空业务数据（TRUNCATE 同时重置自增主键） ----------
USE `lms_course`;
TRUNCATE TABLE `course_chapter`;
TRUNCATE TABLE `course_catalog`;
TRUNCATE TABLE `course_enrollment`;
TRUNCATE TABLE `course`;

USE `lms_learning`;
TRUNCATE TABLE `lesson`;
TRUNCATE TABLE `learning_record`;
TRUNCATE TABLE `note`;
TRUNCATE TABLE `question`;
TRUNCATE TABLE `answer`;
TRUNCATE TABLE `points_record`;
TRUNCATE TABLE `sign_in`;

USE `lms_exam`;
TRUNCATE TABLE `question_biz`;
TRUNCATE TABLE `question`;

USE `lms_media`;
TRUNCATE TABLE `media`;

USE `lms_remark`;
TRUNCATE TABLE `liked_record`;

USE `lms_search`;
TRUNCATE TABLE `user_interests`;

USE `lms_kb`;
TRUNCATE TABLE `rag_eval_sample`;
TRUNCATE TABLE `knowledge_doc`;
TRUNCATE TABLE `knowledge_base`;

USE `lms_ai`;
TRUNCATE TABLE `agent_task`;
TRUNCATE TABLE `agent_profile_job`;
TRUNCATE TABLE `agent_user_profile`;
TRUNCATE TABLE `agent_user_behavior`;
TRUNCATE TABLE `agent_session`;

USE `lms_grab`;
TRUNCATE TABLE `grab_record`;

USE `lms_statistics`;
TRUNCATE TABLE `daily_stats`;

-- ---------- 2. 删除旧账号/档案（先日志与扩展，再主表） ----------
USE `lms_auth`;
TRUNCATE TABLE `login_log`;
TRUNCATE TABLE `account`;

USE `lms_user`;
TRUNCATE TABLE `student_info`;
TRUNCATE TABLE `teacher_info`;
TRUNCATE TABLE `user`;

-- ---------- 3. 重建演示账号 ----------
-- 账号：t_demo（教师 user_type=2） / s_demo（学生 user_type=1），密码 123456
USE `lms_auth`;
INSERT INTO `account` (`username`, `password`, `user_type`, `user_id`, `status`, `deleted`)
VALUES ('t_demo', '$2a$10$R2bm7GBQxO3jshv5T5vAlekLd6wXYiGmv865/ncOUvc.GpUj823SC', 2, NULL, 1, 0);
SET @tAid = LAST_INSERT_ID();
INSERT INTO `account` (`username`, `password`, `user_type`, `user_id`, `status`, `deleted`)
VALUES ('s_demo', '$2a$10$R2bm7GBQxO3jshv5T5vAlekLd6wXYiGmv865/ncOUvc.GpUj823SC', 1, NULL, 1, 0);
SET @sAid = LAST_INSERT_ID();

-- 档案：教师 t_demo / 学生 s_demo
USE `lms_user`;
INSERT INTO `user` (`account_id`, `user_type`, `nickname`, `status`, `deleted`)
VALUES (@tAid, 2, '演示教师', 1, 0);
SET @tUid = LAST_INSERT_ID();
INSERT INTO `user` (`account_id`, `user_type`, `nickname`, `status`, `deleted`)
VALUES (@sAid, 1, '演示学生', 1, 0);
SET @sUid = LAST_INSERT_ID();

-- 扩展信息
INSERT INTO `teacher_info` (`user_id`, `college`, `title`, `bio`)
VALUES (@tUid, '计算机学院', '副教授', '演示教师账号（t_demo / 123456）');
INSERT INTO `student_info` (`user_id`, `student_no`, `major`, `grade`, `class_name`)
VALUES (@sUid, '2026001', '软件工程', '2026级', '软工2601');

-- 回填 account.user_id，完成账号 ↔ 档案关联
USE `lms_auth`;
UPDATE `account` SET `user_id` = @tUid WHERE `id` = @tAid;
UPDATE `account` SET `user_id` = @sUid WHERE `id` = @sAid;
