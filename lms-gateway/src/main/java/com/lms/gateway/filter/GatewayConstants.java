package com.lms.gateway.filter;

/**
 * 网关本地常量
 *
 * 与 lms-common / lms-auth 保持一致的 JWT 与用户头约定。
 * 网关不引 lms-common（避免把 Web/MVC/ORM 自动配置带入 WebFlux 网关），因此常量本地维护一份。
 */
public interface GatewayConstants {

    /** 认证请求头，值形如 "Bearer <token>" */
    String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 前缀，与认证服务签发格式一致 */
    String TOKEN_PREFIX = "Bearer ";

    /** 网关透传用户信息头（JSON：{"userId":..,"userType":..}），业务服务据此识别当前用户 */
    String HEADER_USER_INFO = "user-info";

    /** JWT payload key（与 lms-common JwtUtils 一致）：用户档案 id */
    String CLAIM_USER_ID = "userId";
    /** JWT payload key：用户类型（1 学生 / 2 教师） */
    String CLAIM_USER_TYPE = "userType";
    /** JWT payload key：随机唯一 id，登出黑名单的凭据 */
    String CLAIM_JTI = "jti";

    /** 登出黑名单 key 前缀（与 lms-auth RedisConstants 一致），完整 key = 前缀 + jti */
    String JWT_BLACKLIST_KEY = "lms:jwt:blacklist:";

    /** 未登录统一响应体（与统一响应 R 结构一致，code=401） */
    String UNAUTHORIZED_BODY = "{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}";
}
