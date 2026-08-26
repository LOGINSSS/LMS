package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提问表单入参
 */
@Data
@Schema(description = "提问表单")
public class QaQuestionFormDTO {

    /** 所属课程 id */
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    /** 问题标题 */
    @NotBlank(message = "问题标题不能为空")
    private String title;

    /** 问题详情 */
    private String content;
}
