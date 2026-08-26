package com.lms.learning.learning.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.learning.learning.domain.dto.AnswerFormDTO;
import com.lms.learning.learning.domain.dto.QaQuestionFormDTO;
import com.lms.learning.learning.domain.query.QaPageQuery;
import com.lms.learning.learning.domain.vo.QaQuestionVO;
import com.lms.learning.learning.service.ILearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互动问答接口（/qa 前缀规避 exam 服务的 /questions 路由）
 *
 * 职责：课程内提问与回答，提问/回答发放对应积分。
 */
@Tag(name = "互动问答接口")
@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class QaController {

    private final ILearningService learningService;

    /** 发布提问 */
    @PostMapping("/questions")
    @Operation(summary = "发布提问")
    public R<Long> ask(@RequestBody @Valid QaQuestionFormDTO dto) {
        return R.ok(learningService.askQuestion(dto));
    }

    /** 按课程分页查问题（含回答列表） */
    @GetMapping("/questions/page")
    @Operation(summary = "按课程分页查问题")
    public R<PageDTO<QaQuestionVO>> listQuestions(QaPageQuery query) {
        return R.ok(learningService.listQuestions(query));
    }

    /** 回答问题 */
    @PostMapping("/questions/{id}/answers")
    @Operation(summary = "回答问题")
    public R<Long> answer(@PathVariable("id") Long questionId, @RequestBody @Valid AnswerFormDTO dto) {
        return R.ok(learningService.answerQuestion(questionId, dto));
    }
}
