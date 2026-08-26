package com.lms.statistics.statistics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 热门课程条目（跨服务传输对象，来自 lms-course 热度榜）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseTopDTO {

    /** 课程 id */
    private Long courseId;

    /** 课程名称 */
    private String name;

    /** 选课中人数 */
    private Long enrollCount;
}
