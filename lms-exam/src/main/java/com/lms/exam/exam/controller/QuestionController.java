package com.lms.exam.exam.controller;

import com.lms.common.domain.R;
import com.lms.exam.exam.domain.vo.QuestionVO;
import com.lms.exam.exam.service.IQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目查询接口
 *
 * 职责：考试/练习场景按业务取题，只接收参数并调用服务。
 */
@Tag(name = "题目查询接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final IQuestionService questionService;

    /** 按业务取题（只含启用题，供考试/练习使用） */
    @GetMapping("/biz/{bizId}")
    @Operation(summary = "按业务取题")
    public R<List<QuestionVO>> queryByBizId(@PathVariable("bizId") Long bizId) {
        return R.ok(questionService.queryByBizId(bizId));
    }
}
