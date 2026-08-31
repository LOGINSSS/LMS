package com.lms.exam.exam.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.exam.exam.domain.dto.QuestionFormDTO;
import com.lms.exam.exam.domain.query.QuestionPageQuery;
import com.lms.exam.exam.domain.vo.QuestionVO;
import com.lms.exam.exam.service.IQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目管理接口（教师端）
 *
 * 职责：教师维护题库（增删改查、绑定业务），只接收参数并调用服务。
 */
@Tag(name = "题目管理接口（教师端）")
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final IQuestionService questionService;

    /** 教师新增题目 */
    @PostMapping
    @Operation(summary = "新增题目")
    public R<Long> saveQuestion(@RequestBody @Valid QuestionFormDTO dto) {
        return R.ok(questionService.saveQuestion(dto));
    }

    /** 教师修改题目（仅更新传入字段） */
    @PutMapping("/{id}")
    @Operation(summary = "修改题目")
    public R<Void> updateQuestion(@PathVariable("id") Long id, @RequestBody QuestionFormDTO dto) {
        questionService.updateQuestion(id, dto);
        return R.ok();
    }

    /** 教师删除题目（逻辑删除） */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除题目")
    public R<Void> deleteQuestion(@PathVariable("id") Long id) {
        questionService.deleteQuestion(id);
        return R.ok();
    }

    /** 题目详情 */
    @GetMapping("/{id}")
    @Operation(summary = "题目详情")
    public R<QuestionVO> getQuestion(@PathVariable("id") Long id) {
        return R.ok(questionService.getQuestion(id));
    }

    /** 题目分页（可按题型/分类/难度筛选） */
    @GetMapping("/page")
    @Operation(summary = "题目分页")
    public R<PageDTO<QuestionVO>> queryPage(QuestionPageQuery query) {
        return R.ok(questionService.queryQuestionPage(query));
    }

    /** 题目绑定到业务（1 课程 / 2 章节 / 3 考试卷），可设分值；重复绑定更新分值 */
    @PostMapping("/{id}/biz")
    @Operation(summary = "绑定题目到业务")
    public R<Void> bindToBiz(@PathVariable("id") Long id,
                             @RequestParam("bizType") Integer bizType,
                             @RequestParam("bizId") Long bizId,
                             @RequestParam(value = "score", defaultValue = "0") Integer score) {
        questionService.bindToBiz(id, bizType, bizId, score);
        return R.ok();
    }
}
