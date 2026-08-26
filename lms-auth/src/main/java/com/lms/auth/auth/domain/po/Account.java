package com.lms.auth.auth.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录账号 PO
 *
 * 对应表 account，承载登录凭据与账号状态。
 * 业务含义：账号是认证服务的核心主体，注册产生、登录校验；username 全局唯一
 * （唯一索引 uk_username 兜底并发重复注册）；password 存 BCrypt 密文；
 * user_id 注册成功后回填 lms_user.user.id，关联用户档案。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("account")
public class Account extends BaseEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（手机号/邮箱），全局唯一，account 表 uk_username 唯一索引兜底 */
    private String username;

    /** 密码（BCrypt 加密，禁止明文） */
    private String password;

    /** 用户类型：1 学生 2 教师 */
    private Integer userType;

    /** 关联用户档案 id（lms_user.user.id，注册成功后回填） */
    private Long userId;

    /** 账号状态：1 正常 0 禁用，取值见 UserStatus 枚举 */
    private Integer status;
}
