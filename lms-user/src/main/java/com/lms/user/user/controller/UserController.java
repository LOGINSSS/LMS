package com.lms.user.user.controller;

import com.lms.common.domain.R;
import com.lms.user.user.domain.dto.UserDetailVO;
import com.lms.user.user.domain.dto.UserProfileFormDTO;
import com.lms.user.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户档案接口
 *
 * 职责：面向本人提供详情查询与资料修改，并承接认证服务内部的档案创建 Feign 调用，
 * 只做参数接收与结果返回，不承载业务逻辑。
 */
@Tag(name = "用户档案接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * 创建用户档案（认证服务注册成功后内部 Feign 调用，幂等可重试）
     */
    @PostMapping
    @Operation(summary = "创建用户档案（认证服务内部 Feign 调用）")
    public R<Long> createUserProfile(@RequestBody UserProfileFormDTO dto) {
        return R.ok(userService.createUserProfile(dto));
    }

    /**
     * 当前登录用户详情（用户 id 由公共拦截器从网关 user-info 头写入 UserContext）
     */
    @GetMapping("/me")
    @Operation(summary = "当前登录用户详情")
    public R<UserDetailVO> getMyDetail() {
        return R.ok(userService.getMyDetail());
    }

    /**
     * 修改当前用户资料（仅更新非 null 字段）
     */
    @PutMapping("/me")
    @Operation(summary = "修改当前用户资料")
    public R<Void> updateMyProfile(@RequestBody UserProfileFormDTO dto) {
        userService.updateMyProfile(dto);
        return R.ok();
    }

    /**
     * 按 id 查用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "按 id 查用户详情")
    public R<UserDetailVO> getUserDetail(@PathVariable Long id) {
        return R.ok(userService.getUserDetail(id));
    }
}
