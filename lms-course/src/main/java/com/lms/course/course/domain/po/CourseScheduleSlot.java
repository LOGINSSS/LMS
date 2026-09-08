package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 课程排课模板（course_schedule_slot）：每周几+起止时间+位置；week_type 每周/单周/双周/单次
 *
 * 课表/日历展示 = 按 [日期区间] 展开模板（周循环匹配星期；单次/调课走 date_override）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_schedule_slot")
public class CourseScheduleSlot extends BaseEntity {

    /** 周类型常量 */
    public static final int WEEK_EVERY = 1;
    public static final int WEEK_ODD = 2;
    public static final int WEEK_EVEN = 3;
    public static final int WEEK_ONCE = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 id */
    private Long courseId;

    /** 课次 id（lms-learning，可空） */
    private Long lessonId;

    /** 1每周 2单周 3双周 4单次 */
    private Integer weekType;

    /** 星期：1(周一)..7(周日) */
    private Integer dayOfWeek;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 上课地点 */
    private String location;

    /** 单次/调课例外日期（week_type=4） */
    private LocalDate dateOverride;
}
