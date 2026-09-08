package com.lms.learning.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.BeanUtils;
import com.lms.learning.learning.domain.dto.AssessmentFormDTO;
import com.lms.learning.learning.domain.dto.ExerciseRecordFormDTO;
import com.lms.learning.learning.domain.po.ExerciseRecord;
import com.lms.learning.learning.domain.po.LearningAssessment;
import com.lms.learning.learning.domain.vo.DiagnosisVO;
import com.lms.learning.learning.mapper.ExerciseRecordMapper;
import com.lms.learning.learning.mapper.LearningAssessmentMapper;
import com.lms.learning.learning.service.IEvalDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评测数据服务实现（学习数据中心：做题/错题/测评/学情聚合）
 *
 * 防滥用（spec HEAVY_HARNESS_SPEC §7.4/§12 D12-1：作业/练习允许重做刷分，但上报有服务端日上限，
 * 防止 agent 循环/脚本无限刷做题记录污染学情聚合与活跃天数）：
 * - 每用户每日做题上报上限（默认 200 条，含测评来源）；
 * - 每用户每日测评报告上报上限（默认 30 份）。
 * 数值为代码常量，如需可配置后续提升为配置项。
 */
@Service
@RequiredArgsConstructor
public class EvalDataServiceImpl implements IEvalDataService {

    /** 每用户每日做题记录上报上限（练习+测评来源合计，防刷学情/活跃天数） */
    private static final long DAILY_EXERCISE_MAX = 200;
    /** 每用户每日测评报告上报上限 */
    private static final long DAILY_ASSESSMENT_MAX = 30;

    private final ExerciseRecordMapper exerciseMapper;
    private final LearningAssessmentMapper assessmentMapper;

    @Override
    @Transactional
    public Long recordExercise(Long userId, ExerciseRecordFormDTO dto) {
        long todayCount = exerciseMapper.selectCount(new LambdaQueryWrapper<ExerciseRecord>()
                .eq(ExerciseRecord::getUserId, userId)
                .ge(ExerciseRecord::getCreateTime, LocalDate.now().atStartOfDay()));
        if (todayCount >= DAILY_EXERCISE_MAX) {
            throw new CommonException("今日做题记录上报已达上限（" + DAILY_EXERCISE_MAX
                    + " 条），请明天再继续，或联系老师处理");
        }
        ExerciseRecord record = new ExerciseRecord();
        record.setUserId(userId);
        record.setCourseId(dto.getCourseId());
        record.setQuestionId(dto.getQuestionId() == null ? 0L : dto.getQuestionId());
        record.setQuestionType(dto.getQuestionType());
        record.setKnowledgePoint(dto.getKnowledgePoint());
        record.setUserAnswer(dto.getUserAnswer());
        record.setCorrect(dto.getCorrect() == null ? 0 : dto.getCorrect());
        record.setScore(dto.getScore() == null ? 0 : dto.getScore());
        record.setSource(dto.getSource() == null ? ExerciseRecord.SOURCE_EXERCISE : dto.getSource());
        exerciseMapper.insert(record);
        return record.getId();
    }

    @Override
    public PageDTO<Object> pageMyExercises(Long userId, Long courseId, Boolean onlyWrong,
                                           Integer pageNo, Integer pageSize) {
        int no = pageNo == null ? 1 : pageNo;
        int size = pageSize == null ? 10 : pageSize;
        LambdaQueryWrapper<ExerciseRecord> w = new LambdaQueryWrapper<ExerciseRecord>()
                .eq(ExerciseRecord::getUserId, userId)
                .eq(courseId != null, ExerciseRecord::getCourseId, courseId)
                .eq(Boolean.TRUE.equals(onlyWrong), ExerciseRecord::getCorrect, 0)
                .orderByDesc(ExerciseRecord::getId);
        Page<ExerciseRecord> page = exerciseMapper.selectPage(new Page<>(no, size), w);
        return PageDTO.of(page.getTotal(), new ArrayList<>(page.getRecords()));
    }

    @Override
    @Transactional
    public Long saveAssessment(Long userId, AssessmentFormDTO dto) {
        long todayCount = assessmentMapper.selectCount(new LambdaQueryWrapper<LearningAssessment>()
                .eq(LearningAssessment::getUserId, userId)
                .ge(LearningAssessment::getCreateTime, LocalDate.now().atStartOfDay()));
        if (todayCount >= DAILY_ASSESSMENT_MAX) {
            throw new CommonException("今日测评报告上报已达上限（" + DAILY_ASSESSMENT_MAX
                    + " 份），请明天再继续");
        }
        LearningAssessment assessment = new LearningAssessment();
        assessment.setUserId(userId);
        assessment.setCourseId(dto.getCourseId());
        assessment.setTitle(dto.getTitle());
        assessment.setReport(dto.getReport());
        assessment.setTotalScore(dto.getTotalScore() == null ? 0 : dto.getTotalScore());
        assessment.setKnowledgeMastery(dto.getKnowledgeMastery());
        assessmentMapper.insert(assessment);
        return assessment.getId();
    }

    @Override
    public List<Object> listMyAssessments(Long userId, Long courseId) {
        return assessmentMapper.selectList(new LambdaQueryWrapper<LearningAssessment>()
                        .eq(LearningAssessment::getUserId, userId)
                        .eq(courseId != null, LearningAssessment::getCourseId, courseId)
                        .orderByDesc(LearningAssessment::getId))
                .stream().map(a -> (Object) a).toList();
    }

    @Override
    public DiagnosisVO diagnosis(Long userId) {
        // 1. 取最近 500 条做题记录（诊断输入上限，控制聚合开销）
        List<ExerciseRecord> records = exerciseMapper.selectList(new LambdaQueryWrapper<ExerciseRecord>()
                .eq(ExerciseRecord::getUserId, userId)
                .orderByDesc(ExerciseRecord::getId)
                .last("LIMIT 500"));

        // 2. 统计总量/正确率/活跃天数（活跃天数按做题日期去重）
        DiagnosisVO vo = new DiagnosisVO();
        vo.setTotalExercises((long) records.size());
        long correct = records.stream().filter(r -> r.getCorrect() != null && r.getCorrect() == 1).count();
        vo.setTotalCorrect(correct);
        vo.setAccuracy(records.isEmpty() ? 0D : Math.round(correct * 10000D / records.size()) / 100D);
        vo.setActiveDays(records.stream().map(r -> r.getCreateTime().toLocalDate()).distinct().count());

        // 3. 按知识点聚合掌握度（无知识点标签的做题不参与）
        Map<String, List<ExerciseRecord>> byKp = records.stream()
                .filter(r -> r.getKnowledgePoint() != null && !r.getKnowledgePoint().isBlank())
                .collect(Collectors.groupingBy(ExerciseRecord::getKnowledgePoint, LinkedHashMap::new, Collectors.toList()));
        List<DiagnosisVO.KnowledgeStat> stats = new ArrayList<>();
        for (Map.Entry<String, List<ExerciseRecord>> e : byKp.entrySet()) {
            DiagnosisVO.KnowledgeStat stat = new DiagnosisVO.KnowledgeStat();
            stat.setKnowledgePoint(e.getKey());
            stat.setTotal((long) e.getValue().size());
            long ok = e.getValue().stream().filter(r -> r.getCorrect() != null && r.getCorrect() == 1).count();
            stat.setCorrect(ok);
            stat.setAccuracy(Math.round(ok * 10000D / e.getValue().size()) / 100D);
            stats.add(stat);
        }
        // 4. 取薄弱知识点：正确率升序前 3（诊断智能体直接消费）
        List<String> weakPoints = stats.stream()
                .sorted(Comparator.comparing(DiagnosisVO.KnowledgeStat::getAccuracy))
                .limit(3)
                .map(DiagnosisVO.KnowledgeStat::getKnowledgePoint)
                .toList();
        vo.setKnowledgeStats(stats);
        vo.setWeakPoints(weakPoints);
        return vo;
    }

    @Override
    public Map<String, Object> homeworkExamStats(Long userId) {
        // 作业(3)/考试(4)来源的全部记录
        List<ExerciseRecord> records = exerciseMapper.selectList(new LambdaQueryWrapper<ExerciseRecord>()
                .eq(ExerciseRecord::getUserId, userId)
                .in(ExerciseRecord::getSource, ExerciseRecord.SOURCE_HOMEWORK, ExerciseRecord.SOURCE_EXAM)
                .orderByDesc(ExerciseRecord::getId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("homework", summarize(records, ExerciseRecord.SOURCE_HOMEWORK));
        result.put("exam", summarize(records, ExerciseRecord.SOURCE_EXAM));
        // 最近 8 条明细（时间序新→旧）
        List<Map<String, Object>> recent = new ArrayList<>();
        for (ExerciseRecord r : records.stream().limit(8).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("courseId", r.getCourseId());
            m.put("questionId", r.getQuestionId());
            m.put("source", r.getSource());
            m.put("correct", r.getCorrect());
            m.put("score", r.getScore());
            m.put("createTime", r.getCreateTime() == null ? null : r.getCreateTime().toString());
            recent.add(m);
        }
        result.put("recent", recent);
        return result;
    }

    /** 按来源汇总：总题数 / 答对题数 / 得分合计 / 正确率 */
    private Map<String, Object> summarize(List<ExerciseRecord> records, int source) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<ExerciseRecord> sub = records.stream()
                .filter(r -> r.getSource() != null && r.getSource() == source)
                .toList();
        long total = sub.size();
        long correct = sub.stream().filter(r -> r.getCorrect() != null && r.getCorrect() == 1).count();
        long score = sub.stream().mapToLong(r -> r.getScore() == null ? 0 : r.getScore()).sum();
        m.put("total", total);
        m.put("correct", correct);
        m.put("score", score);
        m.put("rate", total == 0 ? 0D : Math.round(correct * 10000D / total) / 100D);
        return m;
    }
}
