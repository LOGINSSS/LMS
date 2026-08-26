package com.lms.exam.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 题目表单入参（教师建题/改题共用）
 *
 * 使用场景：POST /admin/questions 与 PUT /admin/questions/{id} 的请求体。
 * 修改场景未传字段不更新（服务层按 null 判断）。
 */
@Data
@Schema(description = "题目表单")
public class QuestionFormDTO {

    /** 题干 */
    @NotBlank(message = "题干不能为空")
    private String name;

    /** 题型：1单选 2多选 3判断，取值见 QuestionType 枚举 */
    @NotNull(message = "题型不能为空")
    private Integer type;

    /** 题目分类（如 微服务/Java/数据库） */
    private String category;

    /** 难度：1易 2中 3难，取值见 Difficulty 枚举 */
    @NotNull(message = "难度不能为空")
    private Integer difficulty;

    /** 答案解析 */
    private String analysis;

    /** 答案（JSON 格式见 Question 实体注释） */
    private String answer;
}
