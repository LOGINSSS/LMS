package com.lms.learning.learning.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.learning.learning.domain.dto.AssessmentFormDTO;
import com.lms.learning.learning.domain.dto.ExerciseRecordFormDTO;
import com.lms.learning.learning.domain.vo.DiagnosisVO;

/**
 * 评测数据服务（学习数据中心，需求文档 §4.7）
 *
 * 职责：做题记录/错题集、测评报告的存储与查询、学情聚合（诊断智能体输入）。
 * 数据回流闭环：测评产生的错题/成绩写入，下一轮诊断读取。
 */
public interface IEvalDataService {

    /** 上报做题记录（练习/测评作答，来源字段区分） */
    Long recordExercise(Long userId, ExerciseRecordFormDTO dto);

    /** 我的做题记录分页（可按错题过滤） */
    PageDTO<Object> pageMyExercises(Long userId, Long courseId, Boolean onlyWrong, Integer pageNo, Integer pageSize);

    /** 上报测评结果（报告 + 掌握度，闭环回流） */
    Long saveAssessment(Long userId, AssessmentFormDTO dto);

    /** 我的测评报告列表 */
    java.util.List<Object> listMyAssessments(Long userId, Long courseId);

    /** 学情聚合（诊断智能体输入：做题/正确率/知识点掌握/薄弱点/活跃度） */
    DiagnosisVO diagnosis(Long userId);
}
