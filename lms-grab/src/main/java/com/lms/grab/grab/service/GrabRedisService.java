package com.lms.grab.grab.service;

import com.lms.grab.grab.config.GrabProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 抢课 Redis 服务（spec 0.2 §4.3~4.4）
 *
 * 职责：
 * 1. 预热：课程发布时把抢课窗口与库存写入 Redis（lms-course 发布成功后调用）；
 * 2. 预检：学生抢课走 Lua 原子脚本（窗口判断 + 去重 + 扣减），单次往返防超卖防重复；
 * 3. 查询：剩余库存 / 是否已抢。
 */
@Service
@RequiredArgsConstructor
public class GrabRedisService {

    private final StringRedisTemplate redisTemplate;
    private final GrabProperties properties;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lua 原子抢课脚本（KEYS: [window, stock, users]  ARGV: [courseId, userId, now]）
     * 返回：0 已抢过 / -1 窗口未开或已结束 / -2 售罄 / 1 成功
     */
    private static final String LUA_GRAB =
            "local window = redis.call('HGET', KEYS[1], 'start') or ''\n" +
            "local endTime = redis.call('HGET', KEYS[1], 'end') or ''\n" +
            "if window == '' or endTime == '' then return -1 end\n" +
            "if ARGV[3] < window or ARGV[3] > endTime then return -1 end\n" +
            "if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then return 0 end\n" +
            "local left = redis.call('DECR', KEYS[2])\n" +
            "if left < 0 then redis.call('INCR', KEYS[2]); return -2 end\n" +
            "redis.call('SADD', KEYS[3], ARGV[2])\n" +
            "redis.call('HSET', KEYS[4], ARGV[2], ARGV[3])\n" +
            "return 1";

    /** 预热：写入窗口与库存（幂等，重复预热重置库存） */
    public void prepare(Long courseId, LocalDateTime grabStartTime, LocalDateTime grabEndTime, Integer stock) {
        String windowKey = properties.windowKey(courseId);
        String stockKey = properties.stockKey(courseId);
        String usersKey = properties.usersKey(courseId);
        String detailKey = properties.detailKey(courseId);

        redisTemplate.opsForHash().putAll(windowKey, java.util.Map.of(
                "start", grabStartTime.format(FMT),
                "end", grabEndTime.format(FMT)));
        // 库存预热 = 课程总名额（0=不限时给个大数兜底）
        int s = stock == null || stock <= 0 ? Integer.MAX_VALUE : stock;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(s));
        // 清空旧去重集合（重新发布场景）
        redisTemplate.delete(usersKey);
        redisTemplate.delete(detailKey);
    }

    /**
     * 原子抢课：返回结果码
     * 1 成功 / 0 已抢过 / -1 窗口未开或已结束 / -2 售罄
     */
    public int grab(Long courseId, Long userId, LocalDateTime now) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_GRAB, Long.class);
        Long result = redisTemplate.execute(script,
                Collections.singletonList(properties.windowKey(courseId)),
                properties.windowKey(courseId), properties.stockKey(courseId),
                properties.usersKey(courseId), properties.detailKey(courseId),
                String.valueOf(courseId), String.valueOf(userId), now.format(FMT));
        return result == null ? -1 : result.intValue();
    }

    /** 剩余库存（未预热返回 null） */
    public Integer leftStock(Long courseId) {
        String v = redisTemplate.opsForValue().get(properties.stockKey(courseId));
        if (v == null) {
            return null;
        }
        return Math.max(0, Integer.parseInt(v));
    }

    /** 是否已抢 */
    public boolean hasGrabbed(Long courseId, Long userId) {
        Boolean member = redisTemplate.opsForSet().isMember(properties.usersKey(courseId), String.valueOf(userId));
        return Boolean.TRUE.equals(member);
    }

    /** 读取窗口时间（未预热返回 null） */
    public java.util.Map<Object, Object> window(Long courseId) {
        return redisTemplate.opsForHash().entries(properties.windowKey(courseId));
    }
}
