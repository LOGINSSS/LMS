package com.lms.course.course.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热门课程出参（数据中心看板用）
 *
 * 业务含义：按选课中人数降序的课程热度榜条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热门课程条目")
public class CourseTopVO {

    /** 课程 id */
    private Long courseId;

    /** 课程名称 */
    private String name;

    /** 选课中人数 */
    private Long enrollCount;
}
