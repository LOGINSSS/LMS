-- ============================================================
-- 演示数据清理脚本（可选）
--
-- 用途：一键清空登录模块验证过程中产生的演示数据，把库恢复到
-- 「只有表结构、没有业务数据」的干净状态，方便重新演示或对接真实数据。
-- 执行方式：将本文件拷贝进 lms-mysql 容器后执行，例如：
--   docker cp scripts/cleanup-demo-data.sql lms-mysql:/tmp/cleanup.sql
--   docker exec lms-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 < /tmp/cleanup.sql"
-- 注意：只清理演示数据，不删表、不删库；执行前请确认无需保留当前数据。
-- ============================================================

--1. 清理 lms_user 档案与扩展表：先删扩展信息（学生/教师），再删用户主表
--   原因：teacher_info / student_info 通过 user_id 外键语义关联 user，先删子表避免悬空引用
DELETE FROM `lms_user`.`student_info`;
DELETE FROM `lms_user`.`teacher_info`;
DELETE FROM `lms_user`.`user`;

--2. 清理 lms_auth 登录凭据与日志：先删日志，再删账号
--   原因：login_log 记录账号 id，先删日志再删账号保持数据一致
DELETE FROM `lms_auth`.`login_log`;
DELETE FROM `lms_auth`.`account`;

--3. 重置自增主键：演示数据重建时 id 从 1 开始，便于对照文档示例
--   注意：仅练手环境使用，生产环境禁止重置自增
ALTER TABLE `lms_user`.`user` AUTO_INCREMENT = 1;
ALTER TABLE `lms_user`.`teacher_info` AUTO_INCREMENT = 1;
ALTER TABLE `lms_user`.`student_info` AUTO_INCREMENT = 1;
ALTER TABLE `lms_auth`.`account` AUTO_INCREMENT = 1;
ALTER TABLE `lms_auth`.`login_log` AUTO_INCREMENT = 1;
