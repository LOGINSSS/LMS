package com.lms.course.course.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 章节正文表单入参（教师保存某章 markdown 正文）
 *
 * 使用场景：PUT /admin/catalog/{catalogId}/chapter。
 */
@Data
@Schema(description = "章节正文表单")
public class ChapterFormDTO {

    /** 章节 markdown 正文 */
    @NotBlank(message = "正文不能为空")
    private String contentMd;
}
