package com.lms.exam.exam.controller;

import com.lms.common.domain.R;
import com.lms.exam.exam.domain.dto.PaperCreateFormDTO;
import com.lms.exam.exam.domain.dto.PaperSubmitDTO;
import com.lms.exam.exam.domain.vo.ExamPaperVO;
import com.lms.exam.exam.service.IExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 试卷快照接口（v1 收尾：出卷流程骨干，作业/考试 rails 共用）
 *
 * 教师端（/admin/exam-papers/**，仅老师）：组卷（题库选题快照）、发布、我的卷列表、卷管理视图（含答案）；
 * 学生端（/exam-papers/**，登录即可）：答题视图（已发布卷，剥离答案/解析）、提交作答（服务端确定性判分）。
 */
@Tag(name = "试卷快照接口（出卷流程）")
@RestController
@RequiredArgsConstructor
public class ExamPaperController {

    private final IExamPaperService paperService;

    // ---------- 教师端 ----------

    @PostMapping("/admin/exam-papers")
    @Operation(summary = "组卷（老师）：题库选题 → 快照成卷（草稿）")
    public R<Long> create(@RequestBody @Valid PaperCreateFormDTO dto) {
        return R.ok(paperService.create(dto));
    }

    @PostMapping("/admin/exam-papers/{id}/publish")
    @Operation(summary = "发布卷面（组卷者本人）")
    public R<Void> publish(@PathVariable("id") Long id) {
        paperService.publish(id);
        return R.ok();
    }

    @GetMapping("/admin/exam-papers")
    @Operation(summary = "我组的卷列表（老师，含答案）")
    public R<List<ExamPaperVO>> listMine() {
        return R.ok(paperService.listMine());
    }

    @GetMapping("/admin/exam-papers/{id}")
    @Operation(summary = "卷面管理视图（组卷者本人，含答案/解析）")
    public R<ExamPaperVO> adminView(@PathVariable("id") Long id) {
        return R.ok(paperService.adminView(id));
    }

    // ---------- 学生端 ----------

    @GetMapping("/exam-papers/{id}")
    @Operation(summary = "答题视图（已发布卷，剥离答案/解析）")
    public R<ExamPaperVO> studentView(@PathVariable("id") Long id) {
        return R.ok(paperService.studentView(id));
    }

    @PostMapping("/exam-papers/{id}/submit")
    @Operation(summary = "提交作答 → 服务端确定性判分（作业允许多次重做）")
    public R<Map<String, Object>> submit(@PathVariable("id") Long id,
                                         @RequestBody @Valid PaperSubmitDTO dto) {
        return R.ok(paperService.submit(id, dto));
    }
}
