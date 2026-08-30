package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.utils.UserContext;
import com.lms.learning.learning.domain.dto.AssessmentFormDTO;
import com.lms.learning.learning.domain.dto.ExerciseRecordFormDTO;
import com.lms.learning.learning.domain.vo.DiagnosisVO;
import com.lms.learning.learning.service.IEvalDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习数据中心接口（评测业务线，需求文档 §4.7）
 *
 * 职责：做题记录/错题集上报与查询、测评报告上报与查询、学情聚合（诊断智能体输入）。
 * 数据回流闭环：测评产生的错题/成绩写入，下一轮诊断读取。
 */
@Tag(name = "学习数据中心接口（评测业务线）")
@RestController
@RequestMapping("/learn")
@RequiredArgsConstructor
public class EvalDataController {

    private final IEvalDataService evalDataService;

    @PostMapping("/exercises")
    @Operation(summary = "上报做题记录（练习/测评作答，来源区分）")
    public R<Long> recordExercise(@RequestBody @Valid ExerciseRecordFormDTO dto) {
        return R.ok(evalDataService.recordExercise(UserContext.getUser(), dto));
    }

    @GetMapping("/exercises/mine")
    @Operation(summary = "我的做题记录分页（可按错题过滤）")
    public R<PageDTO<Object>> myExercises(@RequestParam(value = "courseId", required = false) Long courseId,
                                          @RequestParam(value = "onlyWrong", required = false) Boolean onlyWrong,
                                          @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(evalDataService.pageMyExercises(UserContext.getUser(), courseId, onlyWrong, pageNo, pageSize));
    }

    @PostMapping("/assessments")
    @Operation(summary = "上报测评结果（报告 + 知识点掌握度，闭环回流）")
    public R<Long> saveAssessment(@RequestBody @Valid AssessmentFormDTO dto) {
        return R.ok(evalDataService.saveAssessment(UserContext.getUser(), dto));
    }

    @GetMapping("/assessments/mine")
    @Operation(summary = "我的测评报告列表")
    public R<List<Object>> myAssessments(@RequestParam(value = "courseId", required = false) Long courseId) {
        return R.ok(evalDataService.listMyAssessments(UserContext.getUser(), courseId));
    }

    @GetMapping("/stats/diagnosis")
    @Operation(summary = "学情聚合（诊断智能体输入：做题/正确率/知识点掌握/薄弱点/活跃度）")
    public R<DiagnosisVO> diagnosis() {
        return R.ok(evalDataService.diagnosis(UserContext.getUser()));
    }
}
