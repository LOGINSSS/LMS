package com.lms.user.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生扩展信息实体
 *
 * 对应表 lms_user.student_info，与 user 主表一对一（uk_user_id 唯一键），
 * 仅 userType = 1（学生）的用户写入，承载学生特有的学号、专业、年级、班级等扩展档案。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_info")
public class StudentInfo extends BaseEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户档案 id：指向 user.id，唯一（uk_user_id） */
    private Long userId;

    /** 学号 */
    private String studentNo;

    /** 专业 */
    private String major;

    /** 年级 */
    private String grade;

    /** 班级 */
    private String className;
}
