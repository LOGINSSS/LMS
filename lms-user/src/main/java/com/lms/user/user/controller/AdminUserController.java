package com.lms.user.user.controller;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import com.lms.user.user.domain.dto.UserVO;
import com.lms.user.user.domain.query.UserPageQuery;
import com.lms.user.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（管理端）
 *
 * 职责：管理后台用户列表分页查询，只做参数接收与结果返回，不承载业务逻辑。
 * 分页参数继承自 UserPageQuery（PageQuery），经 @Validated 触发 pageNo / pageSize 的 @Min 校验。
 */
@Tag(name = "用户管理接口（管理端）")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final IUserService userService;

    /**
     * 分页查询用户（可按类型筛选学生/教师，或按昵称/手机号/邮箱关键字模糊搜索）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询用户（可按类型筛选学生/教师）")
    public R<PageDTO<UserVO>> queryUserPage(UserPageQuery query) {
        return R.ok(userService.queryUserPage(query));
    }
}
