package com.lms.grab.grab.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * lms-course 选课资格 Feign（抢课预检前强校验课程 enroll_rule，防绕过规则抢课）
 *
 * 当前抢课学生身份经 UserInfoFeignConfig 补 user-info 头透传给 lms-course。
 */
@FeignClient(name = "lms-course", contextId = "grabCourseEligibilityClient")
public interface CourseAccessClient {

    /** 返回 R 包 data 为 {allowed, reasons:[...], matched,total} */
    @GetMapping("/courses/{courseId}/eligibility")
    R<Map<String, Object>> eligibility(@PathVariable("courseId") Long courseId);
}
