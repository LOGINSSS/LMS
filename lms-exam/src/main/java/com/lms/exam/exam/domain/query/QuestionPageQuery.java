package com.lms.exam.exam.domain.query;

import com.lms.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目分页查询参数
 *
 * 使用场景：教师管理端题目列表的查询入参，按题型/分类/难度可选筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "题目分页查询参数")
public class QuestionPageQuery extends PageQuery {

    /** 题型精确筛选（1单选 2多选 3判断，可选） */
    private Integer type;

    /** 分类精确筛选（可选） */
    private String category;

    /** 难度精确筛选（1易 2中 3难，可选） */
    private Integer difficulty;
}
