package com.lms.learning.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 签到实体（0.2 改为课程维度）
 *
 * 对应表 sign_in。业务含义：用户在课程页的每日签到记录，
 * 同一用户同一课程同一天唯一（uk_user_course_date），签到发放签到积分并计入课程榜。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sign_in")
public class SignIn extends BaseEntity {

    /** 签到记录 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 签到用户（lms_user.user.id） */
    private Long userId;

    /** 课程 id（0=全局历史数据） */
    private Long courseId;

    /** 签到日期 */
    private LocalDate signDate;
}
