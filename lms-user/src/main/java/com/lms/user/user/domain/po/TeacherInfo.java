package com.lms.user.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教师扩展信息实体
 *
 * 对应表 lms_user.teacher_info，与 user 主表一对一（uk_user_id 唯一键），
 * 仅 userType = 2（教师）的用户写入，承载教师特有的院系、职称、简介等扩展档案。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("teacher_info")
public class TeacherInfo extends BaseEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户档案 id：指向 user.id，唯一（uk_user_id） */
    private Long userId;

    /** 院系 */
    private String college;

    /** 职称 */
    private String title;

    /** 个人简介 */
    private String bio;
}
