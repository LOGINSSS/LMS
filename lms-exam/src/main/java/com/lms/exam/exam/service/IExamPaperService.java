package com.lms.exam.exam.service;

import com.lms.exam.exam.domain.dto.PaperCreateFormDTO;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.vo.ExamPaperVO;

import java.util.List;
import java.util.Map;

/**
 * 试卷快照服务（v1 收尾：出卷流程骨干，作业/考试 rails 共用）
 */
public interface IExamPaperService {

    /** 老师组卷：题库选题 → 快照成卷（草稿态），返回卷 id */
    Long create(PaperCreateFormDTO dto);

    /** 老师发布卷面（owner；汇总总分） */
    void publish(Long id);

    /** 我组的卷列表（老师） */
    List<ExamPaperVO> listMine();

    /** 卷面管理视图（教师视角，含答案/解析） */
    ExamPaperVO adminView(Long id);

    /** 学生答题视图（已发布卷，剥离答案/解析） */
    ExamPaperVO studentView(Long id);

    /** 学生提交作答 → 服务端按卷面答案快照确定性判分，返回得分汇总（不落库，记录轨后续） */
    Map<String, Object> submit(Long id, PaperSubmitDTO dto);
}
