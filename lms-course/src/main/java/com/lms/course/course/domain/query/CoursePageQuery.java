package com.lms.course.course.domain.query;

import com.lms.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程分页查询参数
 *
 * 使用场景：前台课程卡片列表 / 管理端我的课程列表的查询入参。
 * 排序固定按创建时间倒序（PageQuery 约定），不支持前端任意排序字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "课程分页查询参数")
public class CoursePageQuery extends PageQuery {

    /** 课程分类精确筛选（可选，传 null 表示不过滤） */
    private String category;

    /** 关键字模糊匹配课程名称/简介（可选，传 null 表示不过滤） */
    private String keyword;
}
