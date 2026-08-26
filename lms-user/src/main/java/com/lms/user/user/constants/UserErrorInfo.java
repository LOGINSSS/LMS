package com.lms.user.user.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户服务错误码（全局唯一）
 *
 * 2101~2109 为 lms-user 业务错误段，取值不与其它服务冲突；
 * 写法与 CommonError（com.lms.common.enums.CommonError）一致，实现 ErrorInfo 接口。
 */
@Getter
@AllArgsConstructor
public enum UserErrorInfo implements ErrorInfo {

    /** 用户不存在：按 id / accountId 查询档案为空时抛出（本人未建档、管理端查不存在用户） */
    USER_NOT_FOUND(2101, "用户不存在"),
    /** 资料更新失败：更新 user 主表影响行数为 0 时抛出（并发下记录被删或数据未变更） */
    PROFILE_UPDATE_FAILED(2102, "资料更新失败"),
    /** 档案创建失败：插入 user 主表或对应扩展表影响行数不为 1 时抛出 */
    PROFILE_CREATE_FAILED(2103, "档案创建失败");

    private final int code;
    private final String msg;
}
