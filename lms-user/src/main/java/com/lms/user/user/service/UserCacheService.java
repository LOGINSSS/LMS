package com.lms.user.user.service;

import cn.hutool.json.JSONUtil;
import com.lms.user.user.domain.dto.UserDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 用户信息缓存服务（spec 0.2 §5 Redis 缓存体系）
 *
 * Cache-Aside + 主动失效：用户基本信息缓存（user:base:{userId}），
 * 供各模块 Feign 读取加速（替代每次查库）；资料修改后主动删除。
 */
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "user:base";
    private static final long TTL_MINUTES = 30L;

    public UserDetailVO getUserDetail(Long userId, Supplier<UserDetailVO> loader) {
        String key = key(userId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return JSONUtil.toBean(cached, UserDetailVO.class);
        }
        UserDetailVO vo = loader.get();
        if (vo != null) {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo), Duration.ofMinutes(TTL_MINUTES));
        }
        return vo;
    }

    public void evict(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + ":" + userId;
    }
}
