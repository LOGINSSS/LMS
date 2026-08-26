package com.lms.remark.remark.constants;

import com.lms.common.constants.ErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评价互动服务错误码（业务错误码 2401 起，全局唯一）
 */
@Getter
@AllArgsConstructor
public enum LikeErrorInfo implements ErrorInfo {

    /** 点赞对象类型不在 BizType 取值范围内（1课程/2笔记/3问答） */
    BIZ_TYPE_INVALID(2401, "点赞对象类型不合法"),

    /** 点赞对象 id 为空或非正数 */
    BIZ_ID_INVALID(2402, "点赞对象 id 不合法");

    private final int code;
    private final String msg;
}
