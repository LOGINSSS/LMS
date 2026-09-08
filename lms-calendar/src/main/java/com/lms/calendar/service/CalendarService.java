package com.lms.calendar.service;

import com.lms.calendar.client.CourseScheduleClient;
import com.lms.calendar.client.ExamScheduleClient;
import com.lms.calendar.domain.vo.EventVO;
import com.lms.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一日历聚合服务（只读）
 *
 * 事件源：
 * - 上课 class：lms-course /courses/schedule（排课模板展开，按课程集+日期区间）；
 * - 考试 exam / 作业 assignment：lms-exam /exam-schedules/mine（已发布未截止，bizType 区分）；
 * - 活动 activity：预留（后续活动域接入同一契约）。
 * 缓存：按 (userId, 区间) Redis，TTL 5 分钟；各域事件变更走短 TTL 兜底（简化，不做事件总线清理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final CourseScheduleClient courseClient;
    private final ExamScheduleClient examClient;
    private final StringRedisTemplate redisTemplate;

    /** 取区间内我的事件（courseIds 由前端传已报名课程；start/end 为 yyyy-MM-dd，含端点） */
    public List<EventVO> mine(Long userId, List<Long> courseIds, LocalDate start, LocalDate end) {
        if (userId == null || courseIds == null || courseIds.isEmpty() || start == null || end == null) {
            return List.of();
        }
        String cacheKey = "calendar:mine:" + userId + ":" + start + ":" + end;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return JsonUtils.toList(JsonUtils.parseArray(cached), EventVO.class);
            } catch (Exception e) {
                log.warn("日历缓存解析失败（回源）key={}", cacheKey);
            }
        }
        List<EventVO> events = aggregate(courseIds, start, end);
        try {
            redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonStr(events), CACHE_TTL);
        } catch (Exception e) {
            log.warn("日历缓存写入失败 key={}: {}", cacheKey, e.getMessage());
        }
        return events;
    }

    private List<EventVO> aggregate(List<Long> courseIds, LocalDate start, LocalDate end) {
        List<EventVO> events = new ArrayList<>();
        String ids = String.join(",", courseIds.stream().map(String::valueOf).toList());
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.plusDays(1).atStartOfDay();

        // 1. 上课（排课）
        try {
            var r = courseClient.schedule(ids, start.toString(), end.toString());
            if (r != null && r.success() && r.getData() != null) {
                for (Map<String, Object> m : r.getData()) {
                    EventVO e = new EventVO();
                    e.setType("class");
                    e.setRefId(str(m.get("courseId")));
                    e.setTitle(str(m.get("courseName")) + (m.get("location") == null ? "" : " @" + m.get("location")));
                    e.setStart(joinDate(m.get("date"), m.get("startTime")));
                    e.setEnd(joinDate(m.get("date"), m.get("endTime")));
                    e.setColor("#409eff");
                    e.setLocation(str(m.get("location")));
                    e.setCourseId(longOrNull(m.get("courseId")));
                    e.setJump(Map.of("path", "/courses/" + e.getCourseId()));
                    events.add(e);
                }
            }
        } catch (Exception ex) {
            log.warn("日历-排课拉取失败: {}", ex.getMessage());
        }

        // 2. 考试 / 作业（exam_schedule）
        try {
            var r = examClient.mine(ids, null);
            if (r != null && r.success() && r.getData() != null) {
                for (Map<String, Object> m : r.getData()) {
                    Integer biz = m.get("bizType") == null ? 1 : Integer.valueOf(String.valueOf(m.get("bizType")));
                    boolean exam = biz == 1;
                    EventVO e = new EventVO();
                    e.setType(exam ? "exam" : "assignment");
                    e.setRefId(str(m.get("id")));
                    e.setTitle(str(m.get("title")));
                    e.setStart(str(m.get("startTime")));
                    e.setEnd(str(m.get("endTime")));
                    e.setColor(exam ? "#e05b5b" : "#2f9e6e");
                    e.setCourseId(longOrNull(m.get("courseId")));
                    Long paperId = longOrNull(m.get("paperId"));
                    Map<String, Object> jump = new LinkedHashMap<>();
                    if (paperId != null) {
                        jump.put("path", "/exam-papers/" + paperId + "?scheduleId=" + e.getRefId() + "&bizType=" + biz);
                    } else {
                        jump.put("path", "/exam-schedules");
                    }
                    e.setJump(jump);
                    events.add(e);
                }
            }
        } catch (Exception ex) {
            log.warn("日历-考试拉取失败: {}", ex.getMessage());
        }

        // 排序 + 剪掉区间外（边界防御）
        List<EventVO> out = events.stream()
                .filter(e -> within(e, startAt, endAt))
                .sorted(Comparator.comparing(EventVO::getStart))
                .toList();
        return out;
    }

    private boolean within(EventVO e, LocalDateTime startAt, LocalDateTime endAt) {
        try {
            LocalDateTime st = LocalDateTime.parse(e.getStart());
            return !st.isBefore(startAt) && st.isBefore(endAt);
        } catch (Exception ex) {
            return false;
        }
    }

    private String joinDate(Object date, Object time) {
        return str(date) + "T" + str(time == null ? "00:00:00" : time);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
