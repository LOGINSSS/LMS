package com.lms.exam.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 试卷作答提交表单（v1 收尾：服务端按卷面快照答案确定性判分）
 */
@Data
@Schema(description = "试卷作答提交表单")
public class PaperSubmitDTO {

    @Schema(description = "作答列表（按题库题目 id 对应卷内题目）")
    @NotEmpty(message = "作答不能为空")
    @Valid
    private List<Answer> answers;

    @Schema(description = "单题作答")
    @Data
    public static class Answer {

        @Schema(description = "题目 id（卷内题目来源 id）")
        private Long questionId;

        @Schema(description = "学生作答：单选 'A' / 多选 'A,B' / 判断 'true'|'false'")
        private String userAnswer;
    }
}
