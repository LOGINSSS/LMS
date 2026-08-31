package com.lms.learning.learning.service;

import cn.hutool.json.JSONUtil;
import com.lms.learning.learning.domain.vo.MyLearnStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 学习缓存服务（spec 0.2 §5 Redis 缓存体系）
 *
 * Cache-Aside + 主动失效：学情详情缓存（learn:stats:{userId}），
 * 学习行为上报后由调用方主动删除。
 */
@Service
@RequiredArgsConstructor
public class LearningCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "learn:stats";
    private static final long TTL_MINUTES = 10L;

    public MyLearnStatsVO getLearnStats(Long userId, Supplier<MyLearnStatsVO> loader) {
        String key = key(userId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return JSONUtil.toBean(cached, MyLearnStatsVO.class);
        }
        MyLearnStatsVO vo = loader.get();
        if (vo != null) {
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo), Duration.ofMinutes(TTL_MINUTES));
        }
        return vo;
    }

    public void evictLearnStats(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + ":" + userId;
    }
}
