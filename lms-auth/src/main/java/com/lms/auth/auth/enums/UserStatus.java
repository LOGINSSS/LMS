package com.lms.auth.auth.enums;

import com.lms.common.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账号状态枚举
 *
 * 业务含义：描述登录账号是否允许登录，取值落库于 account.status 字段（1 正常 / 0 禁用）。
 */
@Getter
@AllArgsConstructor
public enum UserStatus implements BaseEnum {

    /** 正常：可正常登录 */
    NORMAL(1, "正常"),

    /** 禁用：禁止登录（如管理员封禁） */
    DISABLED(0, "禁用");

    /** 状态码（与 account.status 存储值一致） */
    private final int value;

    /** 状态描述 */
    private final String desc;
}
