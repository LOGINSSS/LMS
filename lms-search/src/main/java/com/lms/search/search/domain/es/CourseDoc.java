package com.lms.search.search.domain.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

/**
 * 课程索引文档（ES 索引 course）
 *
 * 业务含义：课程主数据的可搜索副本，字段覆盖搜索（名称/简介）与筛选/推荐（分类/教师），
 * 由 lms-search 从 lms-course 同步构建（全量重建，简单可靠）。
 * 注意：不含时间字段——LocalDateTime 写入 ES 后读回需要日期类型映射与 converter，
 * 练手场景搜索/推荐不需要时间维度，故省略。
 */
@Data
@Document(indexName = "course")
public class CourseDoc {

    /** 课程 id（与 lms_course.course.id 对应） */
    @Id
    private Long id;

    /** 课程名称（multiMatch 搜索字段） */
    private String name;

    /** 课程简介（multiMatch 搜索字段） */
    private String intro;

    /** 课程分类（keyword 精确匹配，供 term 筛选与推荐） */
    @Field(type = FieldType.Keyword)
    private String category;

    /** 教师昵称快照 */
    private String teacherName;

    /** 价格（元） */
    private BigDecimal price;

    /** 封面图 URL */
    private String cover;
}
