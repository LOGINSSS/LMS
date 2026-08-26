package com.lms.common.constants;

/**
 * 全局常量
 */
public interface Constant {

    /** 实体创建时间字段（数据库列名） */
    String DATA_FIELD_NAME_CREATE_TIME = "create_time";

    /** 实体更新时间字段（数据库列名） */
    String DATA_FIELD_NAME_UPDATE_TIME = "update_time";

    /** 认证请求头（Bearer token） */
    String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 前缀 */
    String TOKEN_PREFIX = "Bearer ";

    /** 网关透传用户信息头（JSON：{"userId":..,"userType":..}） */
    String HEADER_USER_INFO = "user-info";

    /** JWT 签名密钥配置 key（Nacos lms-common.yaml） */
    String JWT_SECRET_KEY = "lms.jwt.secret";

    /** JWT 有效期（秒）配置 key（Nacos lms-common.yaml） */
    String JWT_TTL_KEY = "lms.jwt.ttl";
}
