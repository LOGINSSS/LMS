package com.lms.exam.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 试卷组卷表单（v1 收尾：老师出卷流程）
 */
@Data
@Schema(description = "试卷组卷表单")
public class PaperCreateFormDTO {

    @Schema(description = "卷面标题")
    @NotBlank(message = "卷面标题不能为空")
    private String title;

    @Schema(description = "卷面说明")
    private String description;

    @Schema(description = "关联课程 id（作业/考试场景，可选）")
    private Long courseId;

    @Schema(description = "选题列表（题库题目 id → 分值）")
    @NotEmpty(message = "卷面至少一题")
    private List<ItemSel> items;

    @Schema(description = "选题项")
    @Data
    public static class ItemSel {

        @Schema(description = "题库题目 id")
        @NotNull(message = "题目 id 不能为空")
        private Long id;

        @Schema(description = "分值（默认 1）")
        private Integer score;
    }
}
