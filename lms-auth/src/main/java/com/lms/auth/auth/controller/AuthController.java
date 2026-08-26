package com.lms.auth.auth.controller;

import com.lms.auth.auth.domain.dto.LoginFormDTO;
import com.lms.auth.auth.domain.dto.LoginVO;
import com.lms.auth.auth.domain.dto.RegisterFormDTO;
import com.lms.auth.auth.domain.dto.UserInfoVO;
import com.lms.auth.auth.service.IAuthService;
import com.lms.common.constants.Constant;
import com.lms.common.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 登出 / 当前用户信息
 *
 * 职责：接收前端认证请求、参数校验、调用服务并返回结果，不承载业务逻辑。
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    /**
     * 注册（学生/教师）：创建登录账号并同步在 lms-user 创建档案，任一失败整体回滚
     */
    @PostMapping("/register")
    @Operation(summary = "注册（学生/教师）")
    public R<Void> register(@RequestBody @Valid RegisterFormDTO dto) {
        authService.register(dto);
        return R.ok();
    }

    /**
     * 账号密码登录：校验通过后签发 JWT
     */
    @PostMapping("/login")
    @Operation(summary = "账号密码登录")
    public R<LoginVO> login(@RequestBody @Valid LoginFormDTO dto, HttpServletRequest request) {
        return R.ok(authService.login(dto, request));
    }

    /**
     * 登出：将当前 token 加入黑名单（需带 Authorization 头）
     */
    @PostMapping("/logout")
    @Operation(summary = "登出（token 加入黑名单）")
    public R<Void> logout(@RequestHeader(value = Constant.HEADER_AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return R.ok();
    }

    /**
     * 当前登录用户基本信息：解析 token 直接返回（不查库）
     */
    @GetMapping("/me")
    @Operation(summary = "当前登录用户基本信息（解析 token）")
    public R<UserInfoVO> me(@RequestHeader(value = Constant.HEADER_AUTHORIZATION, required = false) String authorization) {
        return R.ok(authService.me(authorization));
    }
}
