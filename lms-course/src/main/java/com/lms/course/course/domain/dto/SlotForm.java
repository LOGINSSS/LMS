package com.lms.course.course.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 排课模板表单（老师为课程添加一节排课）
 */
@Data
@Schema(description = "排课模板表单")
public class SlotForm {

    @Schema(description = "课次 id（lms-learning，可选）")
    private Long lessonId;

    @Schema(description = "周类型：1每周 2单周 3双周 4单次")
    @NotNull(message = "周类型不能为空")
    @Min(1)
    @Max(4)
    private Integer weekType;

    @Schema(description = "星期：1(周一)..7(周日)；单次时按 dateOverride 起算")
    @NotNull(message = "星期不能为空")
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @Schema(description = "开始时间")
    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @Schema(description = "上课地点")
    private String location;

    @Schema(description = "单次/调课日期（weekType=4 必填）")
    private LocalDate dateOverride;
}
