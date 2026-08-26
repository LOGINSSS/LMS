package com.lms.search.search.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程卡片信息（跨服务传输对象）
 *
 * 用途：同步索引时从 lms-course 分页拉取课程数据，字段与 lms-course 卡片 VO 对齐，
 * 未知字段忽略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseCardDTO {

    /** 课程 id */
    private Long id;

    /** 课程名称（搜索字段） */
    private String name;

    /** 封面图 URL */
    private String cover;

    /** 课程简介（搜索字段） */
    private String intro;

    /** 课程分类（筛选/推荐字段） */
    private String category;

    /** 教师昵称快照 */
    private String teacherName;

    /** 状态：0 下架 / 1 发布（只同步已发布） */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
