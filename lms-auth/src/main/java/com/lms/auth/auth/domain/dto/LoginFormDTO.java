package com.lms.auth.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录表单 DTO
 *
 * 场景：POST /auth/login 的请求体，承载账号密码登录凭据。
 */
@Data
public class LoginFormDTO {

    /** 登录账号 */
    @NotBlank(message = "请输入用户名")
    private String username;

    /** 密码（明文，服务端 BCrypt 校验） */
    @NotBlank(message = "请输入密码")
    private String password;
}
