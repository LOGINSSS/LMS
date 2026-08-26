package com.lms.common.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具（基于 hutool JWT，HS256 对称签名）
 *
 * 用途：统一签发与解析登录令牌，屏蔽 hutool JWT API 细节。
 *
 * 适用场景：
 *   1. 认证服务（lms-auth）登录成功后签发 token：createToken(...)
 *   2. 网关/资源服务校验 token：verify(...) / parseClaims(...)，与签发方共享同一 secret
 *
 * Payload 约定：userId（用户档案 id）、userType（1 学生 / 2 教师）、username（登录账号）、
 * jti（随机唯一 id，登出黑名单用）、iat / exp。
 * 注意：secret 来自 Nacos lms-common.yaml 的 lms.jwt.secret，签发与校验方必须一致。
 */
public class JwtUtils {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_JTI = "jti";

    private JwtUtils() {
    }

    /**
     * 签发 token
     *
     * @param userId     用户 id（关联用户档案）
     * @param userType   用户类型（1 学生 / 2 教师）
     * @param username   登录账号
     * @param secret     签名密钥（≥32 字符，配置在 Nacos lms-common.yaml）
     * @param ttlSeconds 有效期（秒）
     */
    public static String createToken(Long userId, Integer userType, String username, String secret, long ttlSeconds) {
        Date now = new Date();
        Date expire = DateUtil.offsetSecond(now, (int) ttlSeconds);
        return JWT.create()
                .setPayload(CLAIM_USER_ID, userId)
                .setPayload(CLAIM_USER_TYPE, userType)
                .setPayload(CLAIM_USERNAME, username)
                .setPayload(CLAIM_JTI, IdUtil.fastSimpleUUID())
                .setIssuedAt(now)
                .setExpiresAt(expire)
                .setKey(secret.getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    /**
     * 解析并校验 token（验签 + 过期），失败抛异常
     */
    public static Map<String, Object> parseClaims(String token, String secret) {
        JWT jwt = JWT.of(token).setKey(secret.getBytes(StandardCharsets.UTF_8));
        JWTValidator.of(jwt).validateDate(new Date());
        return jwt.getPayloads();
    }

    /**
     * 校验 token 是否有效（验签 + 过期）
     */
    public static boolean verify(String token, String secret) {
        try {
            parseClaims(token, secret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
