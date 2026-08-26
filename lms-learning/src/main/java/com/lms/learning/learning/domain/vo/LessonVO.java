package com.lms.learning.learning.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课次出参
 */
@Data
@Schema(description = "课次信息")
public class LessonVO {

    /** 课次 id */
    private Long id;

    /** 所属课程 id */
    private Long courseId;

    /** 课次名称 */
    private String name;

    /** 关联媒资视频 id */
    private Long mediaId;

    /** 课次排序 */
    private Integer sort;

    /** 创建时间 */
    private LocalDateTime createTime;
}
