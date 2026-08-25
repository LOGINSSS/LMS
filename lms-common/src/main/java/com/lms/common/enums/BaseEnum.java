package com.lms.common.enums;

/**
 * 枚举接口：业务枚举统一实现，便于校验与序列化
 */
public interface BaseEnum {

    /** 枚举值 */
    int getValue();

    /** 枚举描述 */
    String getDesc();
}
