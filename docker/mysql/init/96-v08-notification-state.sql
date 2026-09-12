-- ============================================================
-- v08: backfill teacher QA notifications that already have answers
-- New answers transition type=1 -> type=3 in NotificationService.
-- This migration repairs existing rows created before that transition existed.
-- ============================================================
USE `lms_learning`;

UPDATE `notification` AS n
INNER JOIN (
    SELECT DISTINCT `question_id`
    FROM `answer`
    WHERE `deleted` = 0
) AS answered ON answered.`question_id` = n.`question_id`
SET n.`type` = 3,
    n.`is_read` = 1,
    n.`content` = '问题已回答，可查看详情'
WHERE n.`type` IN (1, 3)
  AND n.`deleted` = 0;
