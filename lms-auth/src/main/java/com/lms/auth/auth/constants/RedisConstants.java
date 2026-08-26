package com.lms.auth.auth.constants;

/**
 * Redis Key 常量（认证服务）
 *
 * 业务含义：统一管理认证服务的 Redis key 格式与过期策略，避免 key 散落各处导致格式不一致。
 */
public interface RedisConstants {

    /** 登出 JWT 黑名单 key 前缀；完整 key 格式 = 前缀 + jti，如 lms:jwt:blacklist:xxx */
    String JWT_BLACKLIST_KEY = "lms:jwt:blacklist:";

    /** 黑名单保留时长，单位：秒；与 token 有效期一致（1 天），保证主动失效覆盖 token 整个生命周期 */
    long JWT_BLACKLIST_TTL = 86400;
}
