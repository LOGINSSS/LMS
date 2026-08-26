package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学习进度上报入参
 *
 * 使用场景：前端播放课次视频时周期性上报进度。
 */
@Data
@Schema(description = "学习进度上报")
public class LearningRecordFormDTO {

    /** 课次 id */
    @NotNull(message = "课次 id 不能为空")
    private Long lessonId;

    /** 学习进度百分比（0-100） */
    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 100, message = "进度不能大于100")
    private Integer progress;
}
