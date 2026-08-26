package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 笔记表单入参（新增/修改笔记）
 */
@Data
@Schema(description = "笔记表单")
public class NoteFormDTO {

    /** 课程 id */
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    /** 课次 id（可空，课程级笔记） */
    private Long lessonId;

    /** 笔记内容 */
    @NotBlank(message = "笔记内容不能为空")
    private String content;
}
