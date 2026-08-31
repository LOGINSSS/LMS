package com.lms.learning.learning.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程积分实时榜服务（spec 0.2 §6.2 ZSET）
 *
 * 每门课一个 ZSET：course:points:{courseId}，member=userId，score=课程内积分合计。
 * 发放积分时 ZINCRBY（与 points_record 流水双写，流水为权威，ZSET 可回源重建）；
 * 实时榜 ZREVRANGE 取 TopN，我的排名 ZREVRANK + ZSCORE。
 */
@Service
@RequiredArgsConstructor
public class PointsZSetService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "course:points";

    private String key(Long courseId) {
        return KEY_PREFIX + ":" + courseId;
    }

    /** 课程内积分累加（member=userId，score+=points） */
    public void incr(Long courseId, Long userId, int points) {
        if (courseId == null || userId == null) {
            return;
        }
        redisTemplate.opsForZSet().incrementScore(key(courseId), String.valueOf(userId), points);
    }

    /** 课程积分榜 TopN（score 降序） */
    public List<Map<String, Object>> board(Long courseId, int size) {
        int safeSize = size < 1 ? 10 : Math.min(size, 100);
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key(courseId), 0, safeSize - 1);
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (String member : members) {
            Double score = redisTemplate.opsForZSet().score(key(courseId), member);
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("userId", Long.valueOf(member));
            row.put("totalPoints", score == null ? 0L : score.longValue());
            list.add(row);
        }
        return list;
    }

    /** 我的课程积分与排名（排名从 1 开始；未上榜返回 null 排名） */
    public Map<String, Object> myRank(Long courseId, Long userId) {
        Map<String, Object> row = new java.util.HashMap<>();
        Double score = redisTemplate.opsForZSet().score(key(courseId), String.valueOf(userId));
        row.put("totalPoints", score == null ? 0L : score.longValue());
        if (score == null) {
            row.put("rank", null);
            return row;
        }
        Long rank = redisTemplate.opsForZSet().reverseRank(key(courseId), String.valueOf(userId));
        row.put("rank", rank == null ? null : rank + 1);
        return row;
    }

    /** 删除课程积分榜（课程下架/结束等场景可选） */
    public void delete(Long courseId) {
        if (courseId != null) {
            redisTemplate.delete(key(courseId));
        }
    }
}
