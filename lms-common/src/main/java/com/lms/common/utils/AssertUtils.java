package com.lms.common.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lms.common.exceptions.BadRequestException;
import com.lms.common.exceptions.BizIllegalException;
import com.lms.common.exceptions.UnauthorizedException;

import java.util.Collection;

/**
 * 断言工具：业务校验失败统一抛公共异常，由全局异常处理器转成统一响应
 */
public class AssertUtils {

    /** 条件不满足 → 参数错误 */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new BadRequestException(message);
        }
    }

    /** 条件满足 → 参数错误 */
    public static void isFalse(boolean condition, String message) {
        if (condition) {
            throw new BadRequestException(message);
        }
    }

    /** 对象为 null → 业务非法 */
    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new BizIllegalException(message);
        }
    }

    /** 对象为 null → 未登录（鉴权场景专用） */
    public static void isNotNull(Object obj, String message) {
        if (obj == null) {
            throw new UnauthorizedException(message);
        }
    }

    /** 集合为空 → 参数错误 */
    public static void notEmpty(Collection<?> coll, String message) {
        if (CollUtil.isEmpty(coll)) {
            throw new BadRequestException(message);
        }
    }

    /** 字符串为空白 → 参数错误 */
    public static void notBlank(String str, String message) {
        if (StrUtil.isBlank(str)) {
            throw new BadRequestException(message);
        }
    }

    /** 直接失败（业务非法） */
    public static void fail(String message) {
        throw new BizIllegalException(message);
    }
}
