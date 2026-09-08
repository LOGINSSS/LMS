package com.lms.exam.exam.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.UserContext;
import com.lms.exam.exam.domain.dto.ExamScheduleFormDTO;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.po.ExamPaper;
import com.lms.exam.exam.domain.po.ExamSchedule;
import com.lms.exam.exam.mapper.ExamPaperMapper;
import com.lms.exam.exam.mapper.ExamScheduleMapper;
import com.lms.exam.exam.service.IExamPaperService;
import com.lms.exam.exam.service.IExamScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发布物排期服务实现（v1 收尾 P2b-③/④：作业/考试发布与交卷）
 *
 * 权限规则：
 * - 发布/结束：仅教师（userType=2），结束限本人发布；发布须挂【本人已发布】的卷面（出卷流程产物）；
 * - 我的发布物：登录即可，按用户课程交集过滤（已发布 + 未截止，含未开始的排期，日历用），可按 bizType 过滤；
 * - 交卷：仅发布状态 + 开始≤now≤截止 窗口内；判分委托卷面服务（按卷面答案快照确定性判分，作业允许多次重做）；
 * - 详情：已发布可见，本人发布可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamScheduleServiceImpl implements IExamScheduleService {

    private final ExamScheduleMapper scheduleMapper;
    private final ExamPaperMapper paperMapper;
    private final IExamPaperService paperService;
    private final com.lms.exam.exam.client.LearningRecordClient recordClient;
    private final com.lms.exam.exam.mq.ExamSubmissionProducer submissionProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(ExamScheduleFormDTO dto) {
        assertTeacher();
        Long current = UserContext.getUser();
        // 参数校验
        int bizType = dto.getBizType() == null ? ExamSchedule.BIZ_EXAM : dto.getBizType();
        AssertUtils.isTrue(bizType == ExamSchedule.BIZ_EXAM || bizType == ExamSchedule.BIZ_HOMEWORK,
                "发布物类型非法：1 考试 / 2 作业");
        AssertUtils.isTrue(dto.getStartTime() != null && dto.getEndTime() != null
                && dto.getEndTime().isAfter(dto.getStartTime()), "截止时间必须晚于开始时间");
        // 卷面校验：存在 + 本人组卷 + 已发布（出卷流程：组卷→发布卷面→再挂排期发布）
        ExamPaper paper = paperMapper.selectById(dto.getPaperId());
        AssertUtils.notNull(paper, "卷面不存在");
        AssertUtils.isTrue(Objects.equals(paper.getTeacherId(), current), "只能挂载自己组卷的卷面");
        AssertUtils.isTrue(paper.getStatus() != null && paper.getStatus() == ExamPaper.STATUS_PUBLISHED,
                "卷面未发布，请先在出卷流程中发布卷面");

        int duration = dto.getDurationMinutes() == null || dto.getDurationMinutes() <= 0
                ? 90 : dto.getDurationMinutes();
        ExamSchedule schedule = new ExamSchedule();
        schedule.setTitle(dto.getTitle());
        schedule.setDescription(dto.getDescription());
        schedule.setBizType(bizType);
        schedule.setPaperId(paper.getId());
        schedule.setCourseIds(dto.getCourseIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        schedule.setTeacherId(current);
        schedule.setDurationMinutes(duration);
        schedule.setStartTime(dto.getStartTime());
        schedule.setEndTime(dto.getEndTime());
        schedule.setStatus(ExamSchedule.STATUS_PUBLISHED);
        scheduleMapper.insert(schedule);
        log.info("发布物排期已发布 teacherId={} type={} title={} paperId={}", current, bizType, dto.getTitle(), paper.getId());
        return schedule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        assertTeacher();
        ExamSchedule schedule = getEntity(id);
        if (!Objects.equals(schedule.getTeacherId(), UserContext.getUser())) {
            throw new ForbiddenException("仅发布者可结束该排期");
        }
        schedule.setStatus(ExamSchedule.STATUS_CLOSED);
        scheduleMapper.updateById(schedule);
    }

    @Override
    public List<ExamSchedule> listMine(List<Long> myCourseIds, Integer bizType) {
        if (myCourseIds == null || myCourseIds.isEmpty()) {
            return List.of();
        }
        Set<String> mine = myCourseIds.stream().map(String::valueOf).collect(Collectors.toSet());
        // 已发布且未截止（含未开始与进行中），日历展示
        List<ExamSchedule> all = scheduleMapper.selectList(new LambdaQueryWrapper<ExamSchedule>()
                .eq(ExamSchedule::getStatus, ExamSchedule.STATUS_PUBLISHED)
                .eq(bizType != null, ExamSchedule::getBizType, bizType)
                .gt(ExamSchedule::getEndTime, LocalDateTime.now())
                .orderByAsc(ExamSchedule::getStartTime));
        List<ExamSchedule> mineList = new ArrayList<>();
        for (ExamSchedule s : all) {
            if (StrUtil.isBlank(s.getCourseIds())) {
                continue;
            }
            Set<String> scheduleCourses = new HashSet<>(Arrays.asList(s.getCourseIds().split(",")));
            if (scheduleCourses.stream().anyMatch(mine::contains)) {
                mineList.add(s);
            }
        }
        return mineList;
    }

    @Override
    public List<ExamSchedule> listByTeacher() {
        assertTeacher();
        return scheduleMapper.selectList(new LambdaQueryWrapper<ExamSchedule>()
                .eq(ExamSchedule::getTeacherId, UserContext.getUser())
                .orderByDesc(ExamSchedule::getId));
    }

    @Override
    public ExamSchedule detail(Long id) {
        ExamSchedule schedule = getEntity(id);
        Long current = UserContext.getUser();
        boolean published = schedule.getStatus() != null && schedule.getStatus() == ExamSchedule.STATUS_PUBLISHED;
        boolean owner = Objects.equals(schedule.getTeacherId(), current);
        if (!published && !owner) {
            throw new ForbiddenException("该排期不可见");
        }
        return schedule;
    }

    @Override
    public Map<String, Object> submitToSchedule(Long id, PaperSubmitDTO dto) {
        ExamSchedule schedule = getEntity(id);
        AssertUtils.isTrue(schedule.getStatus() != null && schedule.getStatus() == ExamSchedule.STATUS_PUBLISHED,
                "该排期未发布或已结束");
        // 时间窗口：开始 ≤ now ≤ 截止（作业/考试同规则；作业允许多次重做，窗口内可重复提交）
        LocalDateTime now = LocalDateTime.now();
        AssertUtils.isTrue(schedule.getStartTime() != null && !now.isBefore(schedule.getStartTime()),
                "尚未到作答时间");
        AssertUtils.isTrue(schedule.getEndTime() != null && !now.isAfter(schedule.getEndTime()),
                "已过提交截止时间");
        AssertUtils.notNull(schedule.getPaperId(), "该排期未挂载卷面");
        // 判分委托卷面服务（确定性判分，见 ExamPaperServiceImpl.submit）
        Map<String, Object> result = paperService.submit(schedule.getPaperId(), dto);
        Map<String, Object> merged = new LinkedHashMap<>(result);
        merged.put("scheduleId", schedule.getId());
        merged.put("bizType", schedule.getBizType() == null ? ExamSchedule.BIZ_EXAM : schedule.getBizType());
        log.info("排期交卷完成 scheduleId={} type={} paperId={}", id, schedule.getBizType(), schedule.getPaperId());
        // 作答记录回流学习数据中心（best-effort：判分结果已返回，回流失败只告警，不阻塞交卷）
        try {
            pushRecordsToLearning(schedule, dto, result);
        } catch (Exception e) {
            log.warn("作答记录回流 lms-learning 失败（忽略）scheduleId={}: {}", id, e.getMessage());
        }
        return merged;
    }

    /**
     * 作答记录回流：把卷面判分明细（details）逐题写入 lms-learning 做题记录（来源：作业=3 / 考试=4），
     * 以作答学生身份（UserContext → user-info 头）归属；多课程排期按首课程归集（前端后续可按学生课程细化）。
     */
    private void pushRecordsToLearning(ExamSchedule schedule, PaperSubmitDTO dto,
                                       Map<String, Object> result) {
        Long actingUser = UserContext.getUser();
        if (actingUser == null || StrUtil.isBlank(schedule.getCourseIds())) {
            return;
        }
        int source = schedule.getBizType() != null && schedule.getBizType() == ExamSchedule.BIZ_HOMEWORK
                ? 3 : 4; // 作业 3 / 考试 4（ExerciseRecord.SOURCE_*）
        String courseId = schedule.getCourseIds().split(",")[0].trim();
        Map<String, String> answerByQid = new java.util.HashMap<>();
        if (dto != null && dto.getAnswers() != null) {
            for (PaperSubmitDTO.Answer a : dto.getAnswers()) {
                if (a.getQuestionId() != null) {
                    answerByQid.put(String.valueOf(a.getQuestionId()),
                            a.getUserAnswer() == null ? "" : a.getUserAnswer());
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
                body.put("source", source);
                body.put("userAnswer", answerByQid.get(String.valueOf(d.get("questionId"))));
                com.lms.common.domain.R<Long> r = recordClient.recordExercise(body);
                if (r == null || !r.success()) {
                    log.warn("做题记录回流失败 qid={}: {}", d.get("questionId"),
                            r == null ? "无响应" : r.getMsg());
                }
            } catch (Exception e) {
                log.warn("做题记录回流异常 qid={}: {}", d.get("questionId"), e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> submitAsync(Long id, PaperSubmitDTO dto) {
        ExamSchedule schedule = getEntity(id);
        AssertUtils.isTrue(schedule.getStatus() != null && schedule.getStatus() == ExamSchedule.STATUS_PUBLISHED,
                "该排期未发布或已结束");
        AssertUtils.isTrue(schedule.getBizType() != null && schedule.getBizType() == ExamSchedule.BIZ_EXAM,
                "仅考试排期支持异步提交（作业请走同步交卷）");
        LocalDateTime now = LocalDateTime.now();
        AssertUtils.isTrue(schedule.getStartTime() != null && !now.isBefore(schedule.getStartTime()),
                "尚未到考试时间");
        AssertUtils.isTrue(schedule.getEndTime() != null && !now.isAfter(schedule.getEndTime()),
                "考试已结束");
        AssertUtils.notNull(schedule.getPaperId(), "该排期未挂载卷面");
        Long userId = UserContext.getUser();
        AssertUtils.notNull(userId, "未登录");

        // 组装作答 JSON → Kafka（消费端幂等落库 + 判分 + 回流）
        java.util.List<Map<String, Object>> answers = new java.util.ArrayList<>();
        if (dto != null && dto.getAnswers() != null) {
            for (PaperSubmitDTO.Answer a : dto.getAnswers()) {
                if (a.getQuestionId() == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("questionId", a.getQuestionId());
                m.put("userAnswer", a.getUserAnswer());
                answers.add(m);
            }
        }
        String submissionId = cn.hutool.core.util.IdUtil.fastSimpleUUID();
        submissionProducer.send(submissionId, schedule.getId(), userId,
                cn.hutool.json.JSONUtil.toJsonStr(answers));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", submissionId);
        out.put("scheduleId", schedule.getId());
        out.put("bizType", ExamSchedule.BIZ_EXAM);
        out.put("message", "已提交，等待服务端批改");
        return out;
    }

    // ---------- 内部 ----------

    private ExamSchedule getEntity(Long id) {
        ExamSchedule schedule = scheduleMapper.selectById(id);
        AssertUtils.notNull(schedule, "排期不存在");
        return schedule;
    }

    private void assertTeacher() {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可管理发布排期");
        }
    }
}
