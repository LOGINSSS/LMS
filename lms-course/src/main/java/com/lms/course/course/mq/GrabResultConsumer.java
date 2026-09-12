package com.lms.course.course.mq;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lms.course.course.domain.po.CourseEnrollment;
import com.lms.course.course.enums.EnrollmentStatus;
import com.lms.course.course.mapper.CourseEnrollmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 抢课成功事件消费端（spec 0.2 §4.3）
 *
 * 监听 lms-grab 发的 lms-grab-success 消息，幂等异步落库 course_enrollment：
 * - 幂等：uk_course_student 唯一索引 + DuplicateKeyException 兜底；
 * - 失败：抛异常走 Kafka 重试；lms-grab 在未收到回执时安全重投原事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabResultConsumer {

    private final CourseEnrollmentMapper enrollmentMapper;
    private final GrabLandedProducer landedProducer;

    @KafkaListener(topics = "${lms.grab.topic:lms-grab-success}", groupId = "lms-course-grab")
    public void onGrabSuccess(String message) {
        log.debug("收到抢课成功消息: {}", message);
        JSONObject json = JSONUtil.parseObj(message);
        Long courseId = json.getLong("courseId");
        Long userId = json.getLong("userId");
        Long recordId = json.getLong("recordId");
        if (recordId == null) {
            // 兼容修复前已进入 Kafka 的旧消息，避免升级窗口内消息被静默丢弃。
            recordId = json.getLong("grabRecordId");
        }
        if (courseId == null || userId == null || recordId == null) {
            log.warn("抢课消息字段缺失，丢弃: {}", message);
            return;
        }
        // 幂等落库：uk_course_student 兜底
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(userId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE.getValue());
        try {
            enrollmentMapper.insert(enrollment);
            log.info("抢课落库成功 courseId={} studentId={}", courseId, userId);
        } catch (DuplicateKeyException e) {
            // 已选课（选课中或已退课）→ 幂等跳过；已退课则翻回选课中
            CourseEnrollment existed = enrollmentMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getCourseId, courseId)
                            .eq(CourseEnrollment::getStudentId, userId));
            if (existed != null && existed.getStatus() != null
                    && existed.getStatus() == EnrollmentStatus.QUIT.getValue()) {
                existed.setStatus(EnrollmentStatus.ACTIVE.getValue());
                enrollmentMapper.updateById(existed);
            }
        }
        // 首次落库或幂等重复消费都发回执；回执丢失时 grab 重发原事件即可再次触发。
        landedProducer.publish(recordId, courseId, userId);
    }
}
