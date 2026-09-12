package com.lms.learning.learning.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * lms-course 课程归属查询 Feign 契约（发答疑通知用）
 *
 * 用途：学生提问时按课程查归属教师（teacherId）与课程名，向教师信箱写待答通知。
 * 由 UserInfoFeignConfig 透传当前用户头（提问学生身份，登录即可访问）。
 */
@FeignClient(name = "lms-course", contextId = "learningCourseOwnerClient")
public interface CourseOwnerClient {

    @GetMapping("/courses/{id}/owner")
    R<Map<String, Object>> owner(@PathVariable("id") Long id);
}
