package com.lms.auth.auth.domain.dto;

import lombok.Data;

/**
 * 登录结果 VO
 *
 * 场景：POST /auth/login 成功后的响应体，前端凭 token 访问受保护接口。
 */
@Data
public class LoginVO {

    /** JWT token（后续请求放入 Authorization: Bearer xxx 头） */
    private String token;

    /** 用户档案 id */
    private Long userId;

    /** 用户类型：1 学生 2 教师 */
    private Integer userType;

    /** 登录账号 */
    private String username;
}
