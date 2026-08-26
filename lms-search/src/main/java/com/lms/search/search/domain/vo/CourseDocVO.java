package com.lms.search.search.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程搜索结果出参
 *
 * 使用场景：ES 课程搜索/推荐的返回结构，字段覆盖搜索结果卡片展示。
 */
@Data
@Schema(description = "课程搜索结果")
public class CourseDocVO {

    /** 课程 id */
    private Long id;

    /** 课程名称 */
    private String name;

    /** 封面图 URL */
    private String cover;

    /** 课程简介 */
    private String intro;

    /** 课程分类 */
    private String category;

    /** 教师昵称 */
    private String teacherName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
