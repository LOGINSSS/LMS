package com.lms.user.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表 VO
 *
 * 使用场景：管理端分页列表项（GET /admin/users/page）的返回体，
 * 仅含 user 主表公共字段，不含教师 / 学生的扩展信息。
 */
@Data
@Schema(description = "用户列表项")
public class UserVO {

    /** 用户档案 id（user 主表主键） */
    @Schema(description = "用户档案id")
    private Long id;

    /** 用户类型：1 学生 / 2 教师，取值见 UserType 枚举 */
    @Schema(description = "用户类型：1学生 2教师")
    private Integer userType;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像 URL */
    @Schema(description = "头像URL")
    private String avatar;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 状态：1 正常 / 0 禁用 */
    @Schema(description = "状态：1正常 0禁用")
    private Integer status;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
