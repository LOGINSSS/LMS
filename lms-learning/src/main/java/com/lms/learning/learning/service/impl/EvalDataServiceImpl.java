package com.lms.learning.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评测数据服务实现（学习数据中心：做题/错题/测评/学情聚合）
 */
@Service
@RequiredArgsConstructor
public class EvalDataServiceImpl implements IEvalDataService {

    private final ExerciseRecordMapper exerciseMapper;
    private final LearningAssessmentMapper assessmentMapper;

    @Override
    @Transactional
    public Long recordExercise(Long userId, ExerciseRecordFormDTO dto) {
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
}
