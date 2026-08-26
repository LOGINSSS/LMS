package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 回答表单入参
 */
@Data
@Schema(description = "回答表单")
public class AnswerFormDTO {

    /** 回答内容 */
    @NotBlank(message = "回答内容不能为空")
    private String content;
}
