package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 课次表单入参（教师建课次）
 */
@Data
@Schema(description = "课次表单")
public class LessonFormDTO {

    /** 所属课程 id */
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    /** 课次名称 */
    @NotBlank(message = "课次名称不能为空")
    private String name;

    /** 关联媒资视频 id（可空） */
    private Long mediaId;

    /** 课次排序（小在前） */
    private Integer sort;
}
