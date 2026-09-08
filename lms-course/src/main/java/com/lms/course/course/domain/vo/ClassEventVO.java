package com.lms.course.course.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 上课事件（课表/日历统一事件：按日期区间展开后的单节次）
 */
@Data
public class ClassEventVO {

    private Long courseId;
    private String courseName;
    private Long lessonId;

    /** 上课日期 yyyy-MM-dd */
    private LocalDate date;

    /** 星期 1..7 */
    private Integer dayOfWeek;

    private LocalTime startTime;
    private LocalTime endTime;
    private String location;

    /** 来源模板周类型（1每周 2单周 3双周 4单次） */
    private Integer weekType;
}
