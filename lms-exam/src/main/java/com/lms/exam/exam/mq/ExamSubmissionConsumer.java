package com.lms.exam.exam.mq;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lms.exam.config.AsyncUser;
import com.lms.exam.exam.client.LearningRecordClient;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.po.ExamPaper;
import com.lms.exam.exam.domain.po.ExamSchedule;
import com.lms.exam.exam.domain.po.ExamSubmission;
import com.lms.exam.exam.mapper.ExamPaperMapper;
import com.lms.exam.exam.mapper.ExamScheduleMapper;
import com.lms.exam.exam.mapper.ExamSubmissionMapper;
import com.lms.exam.exam.service.IExamPaperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 考试提交消费端（前端轨 §13.3：考试专用锁页 → Kafka 异步提交 → 幂等落库 → 服务端确定性判分 → 回流 learning）
 *
 * 消息 value JSON：{"submissionId":"uuid","scheduleId":1,"userId":2,"answers":[{"questionId":1,"userAnswer":"A"}]}
 * - 幂等：exam_submission.submission_id 唯一索引，DuplicateKeyException 兜底（消息不丢不重）；
 * - 判分：paperService.submit（卷面快照参考答案确定性判分，不触题库）；
 * - 回流：AsyncUser 桥学生身份 → LearningRecordClient 写 lms-learning（source=4 考试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamSubmissionConsumer {

    private final ExamSubmissionMapper submissionMapper;
    private final ExamScheduleMapper scheduleMapper;
    private final ExamPaperMapper paperMapper;
    private final IExamPaperService paperService;
    private final LearningRecordClient recordClient;

    @KafkaListener(topics = "${lms.exam.submission-topic:lms-exam-submission}",
            groupId = "lms-exam-submission")
    public void onSubmission(String message) {
        if (StrUtil.isBlank(message)) {
            return;
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(message);
        } catch (Exception e) {
            log.warn("考试提交消息解析失败，丢弃: {}", message);
            return;
        }
        String submissionId = json.getStr("submissionId");
        Long scheduleId = json.getLong("scheduleId");
        Long userId = json.getLong("userId");
        if (StrUtil.isBlank(submissionId) || scheduleId == null || userId == null) {
            log.warn("考试提交消息字段缺失，丢弃: {}", message);
            return;
        }
        try {
            handle(submissionId, scheduleId, userId, json);
        } catch (Exception e) {
            log.error("考试提交处理异常 submissionId={}: {}", submissionId, e.getMessage());
            markFailed(submissionId, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(String submissionId, Long scheduleId, Long userId, JSONObject json) {
        // 1. 幂等落库：先插 pending，重复消息由唯一键拦截
        ExamSchedule schedule = scheduleMapper.selectById(scheduleId);
        ExamPaper paper = schedule == null || schedule.getPaperId() == null
                ? null : paperMapper.selectById(schedule.getPaperId());
        ExamSubmission sub = new ExamSubmission();
        sub.setSubmissionId(submissionId);
        sub.setScheduleId(scheduleId);
        sub.setUserId(userId);
        sub.setPaperId(paper == null ? 0L : paper.getId());
        sub.setAnswers(json.get("answers") == null ? null : json.get("answers").toString());
        sub.setStatus(ExamSubmission.STATUS_PENDING);
        try {
            submissionMapper.insert(sub);
        } catch (DuplicateKeyException e) {
            log.info("考试提交重复消息（幂等跳过）submissionId={}", submissionId);
            return;
        }

        // 2. 校验排期/卷面
        if (schedule == null || schedule.getPaperId() == null || paper == null) {
            throw new IllegalStateException("考试排期或卷面不存在: scheduleId=" + scheduleId);
        }

        // 3. 组装作答 → 服务端确定性判分（卷面快照参考答案，不触题库）
        JSONArray arr = json.getJSONArray("answers");
        PaperSubmitDTO dto = new PaperSubmitDTO();
        List<PaperSubmitDTO.Answer> answers = new ArrayList<>();
        if (arr != null) {
            for (Object o : arr) {
                JSONObject a = (JSONObject) o;
                PaperSubmitDTO.Answer ans = new PaperSubmitDTO.Answer();
                ans.setQuestionId(a.getLong("questionId"));
                ans.setUserAnswer(a.getStr("userAnswer"));
                answers.add(ans);
            }
        }
        dto.setAnswers(answers);
        Map<String, Object> result = paperService.submit(schedule.getPaperId(), dto);

        // 4. 回写完成态
        int correct = result.get("correctCount") == null ? 0 : (Integer) result.get("correctCount");
        int score = result.get("score") == null ? 0 : (Integer) result.get("score");
        int total = result.get("totalScore") == null ? 0 : (Integer) result.get("totalScore");
        sub.setCorrectCount(correct);
        sub.setScore(score);
        sub.setTotalScore(total);
        sub.setStatus(ExamSubmission.STATUS_DONE);
        sub.setErrorMsg(null);
        submissionMapper.updateById(sub);

        // 5. 回流 lms-learning（考试 source=4；AsyncUser 桥学生身份）
        AsyncUser.set(userId, 1);
        try {
            pushRecords(schedule, json, result);
        } finally {
            AsyncUser.clear();
        }
        log.info("考试提交处理完成 submissionId={} scheduleId={} score={}/{}", submissionId, scheduleId, score, total);
    }

    /** 逐题回流学习数据中心（复用判分 details；best-effort） */
    private void pushRecords(ExamSchedule schedule, JSONObject json, Map<String, Object> result) {
        if (schedule.getCourseIds() == null || schedule.getCourseIds().isBlank()) {
            return;
        }
        String courseId = schedule.getCourseIds().split(",")[0].trim();
        Map<String, String> answerByQid = new LinkedHashMap<>();
        JSONArray arr = json.getJSONArray("answers");
        if (arr != null) {
            for (Object o : arr) {
                JSONObject a = (JSONObject) o;
                if (a.getLong("questionId") != null) {
                    answerByQid.put(String.valueOf(a.getLong("questionId")),
                            a.getStr("userAnswer") == null ? "" : a.getStr("userAnswer"));
                }
            }
        }
        Object detailsObj = result.get("details");
        if (!(detailsObj instanceof List<?> details)) {
            return;
        }
        for (Object o : details) {
            if (!(o instanceof Map<?, ?> d)) {
                continue;
            }
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("courseId", Long.valueOf(courseId));
                body.put("questionId", d.get("questionId"));
                body.put("correct", d.get("correct"));
                body.put("score", d.get("score"));
                body.put("source", 4); // SOURCE_EXAM
                body.put("userAnswer", answerByQid.get(String.valueOf(d.get("questionId"))));
                var r = recordClient.recordExercise(body);
                if (r == null || !r.success()) {
                    log.warn("考试记录回流失败 qid={}: {}", d.get("questionId"),
                            r == null ? "无响应" : r.getMsg());
                }
            } catch (Exception e) {
                log.warn("考试记录回流异常 qid={}: {}", d.get("questionId"), e.getMessage());
            }
        }
    }

    private void markFailed(String submissionId, String error) {
        try {
            submissionMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamSubmission>()
                            .eq(ExamSubmission::getSubmissionId, submissionId)
                            .eq(ExamSubmission::getStatus, ExamSubmission.STATUS_PENDING)
                            .set(ExamSubmission::getStatus, ExamSubmission.STATUS_FAILED)
                            .set(ExamSubmission::getErrorMsg, StrUtil.sub(error, 0, 400)));
        } catch (Exception e) {
            log.warn("考试提交失败态回写异常 submissionId={}: {}", submissionId, e.getMessage());
        }
    }
}
