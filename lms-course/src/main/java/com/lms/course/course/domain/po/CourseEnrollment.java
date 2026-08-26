package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 选课关系实体
 *
 * 对应表 course_enrollment。业务模型：一个课程可被很多学生选课（一对多），
 * 同一学生同一课程唯一（uk_course_student），退课用状态置 0 保留历史。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_enrollment")
public class CourseEnrollment extends BaseEntity {

    /** 选课记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 id（关联 course.id） */
    private Long courseId;

    /** 选课学生 id（lms_user.user.id） */
    private Long studentId;

    /** 选课状态：1 选课中 / 0 已退课，取值见 EnrollmentStatus 枚举 */
    private Integer status;
}
