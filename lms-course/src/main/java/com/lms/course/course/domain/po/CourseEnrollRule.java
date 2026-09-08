package com.lms.course.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 选课资格约束规则（course_enroll_rule，一课程一条；无记录=不限选）
 *
 * rule_json：声明式规则（见 92-v04-course-access.sql 头注释），由 eligibility 服务逐条解析判定，
 * 支持 AND(mode=ALL) / OR(mode=ANY) 与多数据源（学生档案/学习进度/积分/本地选课记录）组合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_enroll_rule")
public class CourseEnrollRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 id */
    private Long courseId;

    /** 规则 JSON：{"mode":"ALL|ANY","rules":[{type,op,values/courseId/value,...}]} */
    private String ruleJson;
}
