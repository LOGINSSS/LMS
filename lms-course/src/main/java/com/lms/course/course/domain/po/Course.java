package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 课程实体
 *
 * 对应表 course。业务模型：一个课程只归属一个教师（teacher_id），
 * 可被多个学生选课（一对多，见 course_enrollment）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course")
public class Course extends BaseEntity {

    /** 课程 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属教师 id（lms_user.user.id），一个课程只属于一个教师 */
    private Long teacherId;

    /** 教师昵称快照（创建时冗余，卡片展示免跨服务联查） */
    private String teacherName;

    /** 课程名称 */
    private String name;

    /** 封面图 URL（卡片封面展示） */
    private String cover;

    /** 课程简介（卡片文案展示） */
    private String intro;

    /** 课程分类（如 微服务/前端/数据库） */
    private String category;


    /** 状态：0 草稿 / 1 待发布 / 2 抢课中 / 3 进行中 / 4 已结束 / 5 已下架，取值见 CourseStatus 枚举 */
    private Integer status;

    /** 抢课总名额（0=不限） */
    private Integer stock;

    /** 抢课开始时间 */
    private LocalDateTime grabStartTime;

    /** 抢课结束时间 */
    private LocalDateTime grabEndTime;
}
