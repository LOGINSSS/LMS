package com.lms.auth.auth.domain.dto;

import lombok.Data;

/**
 * 当前登录用户基本信息 VO
 *
 * 场景：GET /auth/me 的响应体，内容解析自 JWT 载荷（不查库）。
 */
@Data
public class UserInfoVO {

    /** 用户档案 id */
    private Long userId;

    /** 用户类型：1 学生 2 教师 */
    private Integer userType;

    /** 登录账号 */
    private String username;
}
