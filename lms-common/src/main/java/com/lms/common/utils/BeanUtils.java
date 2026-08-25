package com.lms.common.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

import java.util.Collection;
import java.util.List;

/**
 * Bean 工具：继承 hutool BeanUtil，提供 PO ↔ DTO/VO 常用转换
 */
public class BeanUtils extends BeanUtil {

    /**
     * 复制对象（忽略 null 字段）
     */
    public static <T> T copyBean(Object source, Class<T> tClass) {
        return BeanUtil.toBean(source, tClass, CopyOptions.create().setIgnoreNullValue(true));
    }

    /**
     * 复制集合
     */
    public static <T> List<T> copyList(Collection<?> sourceList, Class<T> tClass) {
        return BeanUtil.copyToList(sourceList, tClass);
    }
}
