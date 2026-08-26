package com.lms.auth.auth.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志 PO
 *
 * 对应表 login_log，记录每次登录行为（成功/失败均记录），供审计与异常登录排查。
 * 业务含义：日志写入失败仅告警，不影响登录主流程。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("login_log")
public class LoginLog extends BaseEntity {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账号 id（关联 lms_auth.account.id） */
    private Long accountId;

    /** 用户档案 id（关联 lms_user.user.id，登录成功时回填） */
    private Long userId;

    /** 用户类型：1 学生 2 教师 */
    private Integer userType;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 登录 IP */
    private String ip;

    /** 设备 / UA（取自 User-Agent 请求头） */
    private String device;

    /** 登录结果：1 成功 0 失败 */
    private Integer status;
}
