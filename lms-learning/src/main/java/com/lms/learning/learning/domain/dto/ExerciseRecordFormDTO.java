package com.lms.learning.learning.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 做题记录上报入参（学生端 / 测评智能体 → 学习数据中心）
 */
@Data
@Schema(description = "做题记录上报入参")
public class ExerciseRecordFormDTO {

    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    /** 题目 id（0 表示测评临时题） */
    private Long questionId;

    /** 题型：1单选 2多选 3判断 */
    private Integer questionType;

    /** 对应知识点（题目 category） */
    private String knowledgePoint;

    /** 学生作答（JSON） */
    private String userAnswer;

    /** 是否答对：1对 0错 */
    @NotNull(message = "是否正确不能为空")
    private Integer correct;

    /** 本题得分 */
    private Integer score;

    /** 来源：1练习 2测评 */
    private Integer source;
}
