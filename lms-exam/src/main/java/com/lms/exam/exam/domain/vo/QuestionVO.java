package com.lms.exam.exam.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目出参
 *
 * 使用场景：题目详情、分页列表、按业务取题的统一返回结构。
 */
@Data
@Schema(description = "题目信息")
public class QuestionVO {

    /** 题目 id */
    private Long id;

    /** 题干 */
    private String name;

    /** 题型：1单选 2多选 3判断，取值见 QuestionType 枚举 */
    private Integer type;

    /** 题目分类 */
    private String category;

    /** 难度：1易 2中 3难，取值见 Difficulty 枚举 */
    private Integer difficulty;

    /** 答案解析 */
    private String analysis;

    /** 答案 JSON */
    private String answer;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
