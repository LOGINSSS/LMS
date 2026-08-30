package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 测评结果上报入参（测评智能体 → 学习数据中心，闭环回流）
 */
@Data
@Schema(description = "测评结果上报入参")
public class AssessmentFormDTO {

    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    /** 测评标题 */
    private String title;

    /** 评估报告（智能体输出） */
    private String report;

    /** 总分 */
    private Integer totalScore;

    /** 知识点掌握度 JSON（{知识点: 掌握度0-100}） */
    private String knowledgeMastery;
}
