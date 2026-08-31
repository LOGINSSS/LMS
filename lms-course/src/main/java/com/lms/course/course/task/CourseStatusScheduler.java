package com.lms.course.course.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lms.course.course.domain.po.Course;
import com.lms.course.course.enums.CourseStatus;
import com.lms.course.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 课程状态定时流转任务（0.2 发布状态机）
 *
 * 状态机（spec 0.2 §3.4）：
 *   待发布(1) --到 grab_start_time--> 抢课中(2) --到 grab_end_time--> 进行中(3)
 * 由定时任务按抢课窗口时间扫描驱动，替代人工上下架。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseStatusScheduler {

    private final CourseMapper courseMapper;

    /** 待发布 → 抢课中：每 30 秒扫描一次，到 grab_start_time 的课程进入抢课中 */
    @Scheduled(fixedDelay = 30000)
    public void openGrabWindow() {
        LocalDateTime now = LocalDateTime.now();
        int updated = courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                .eq(Course::getStatus, CourseStatus.PENDING_GRAB.getValue())
                .le(Course::getGrabStartTime, now)
                .set(Course::getStatus, CourseStatus.GRABBING.getValue()));
        if (updated > 0) {
            log.info("课程状态流转：{} 门课程进入抢课中", updated);
        }
    }

    /** 抢课中 → 进行中：每 30 秒扫描一次，到 grab_end_time 的课程进入进行中 */
    @Scheduled(fixedDelay = 30000)
    public void closeGrabWindow() {
        LocalDateTime now = LocalDateTime.now();
        int updated = courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                .eq(Course::getStatus, CourseStatus.GRABBING.getValue())
                .lt(Course::getGrabEndTime, now)
                .set(Course::getStatus, CourseStatus.ONGOING.getValue()));
        if (updated > 0) {
            log.info("课程状态流转：{} 门课程抢课结束，进入进行中", updated);
        }
    }
}
