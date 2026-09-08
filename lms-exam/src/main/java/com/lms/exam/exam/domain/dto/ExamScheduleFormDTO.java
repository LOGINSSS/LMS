package com.lms.exam.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布物排期表单（v1 收尾 P2b-③/④：作业/考试发布）
 */
@Data
@Schema(description = "发布物排期表单（作业/考试）")
public class ExamScheduleFormDTO {

    @Schema(description = "发布物标题（考试/作业名）")
    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "发布物类型：1 考试 2 作业，缺省 1")
    private Integer bizType;

    @Schema(description = "挂载卷面 id（已发布的 exam_paper）")
    @NotNull(message = "卷面不能为空（请先组卷并发布卷面）")
    private Long paperId;

    @Schema(description = "适用课程 id 集合（可多课程）")
    @NotEmpty(message = "适用课程不能为空")
    private List<Long> courseIds;

    @Schema(description = "开始时间（作业=可开始作答；考试=开考）")
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @Schema(description = "截止时间（作业=提交截止；考试=考试结束）")
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime endTime;

    @Schema(description = "作答时长（分钟），默认 90")
    private Integer durationMinutes;
}
