package com.lms.course.course.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程卡片出参（前端卡片展示专用 JSON）
 *
 * 使用场景：课程列表分页、课程详情、我的课程、我选过的课共用此结构，
 * 字段即前端卡片所需（封面/标题/简介/分类/教师/选课人数/时间）。
 */
@Data
@Schema(description = "课程卡片")
public class CourseCardVO {

    /** 课程 id */
    private Long id;

    /** 课程名称（卡片标题） */
    private String name;

    /** 封面图 URL（卡片封面） */
    private String cover;

    /** 课程简介（卡片文案） */
    private String intro;

    /** 课程分类 */
    private String category;

    /** 归属教师 id */
    private Long teacherId;

    /** 教师昵称（卡片署名） */
    private String teacherName;

    /** 状态：0 已下架 / 1 已发布，取值见 CourseStatus 枚举 */
    private Integer status;

    /** 选课人数（实时统计 course_enrollment 中选课中记录数） */
    private Long totalCount;

    /** 创建时间（卡片排序/展示） */
    private LocalDateTime createTime;
}
