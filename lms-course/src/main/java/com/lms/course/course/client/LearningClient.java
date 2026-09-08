package com.lms.course.course.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * lms-learning 学习数据中心 Feign 契约（选课资格判定数据源）
 *
 * - /learn/stats/my：学习统计（pointsTotal 累计积分等）
 * - /lessons/learn/progress?courseId=：我的课程学习进度（course_done 判定）
 * 需登录身份的接口由 UserInfoFeignConfig 按 UserContext 补 user-info 头。
 */
@FeignClient(name = "lms-learning", contextId = "courseLearningClient")
public interface LearningClient {

    @GetMapping("/learn/stats/my")
    R<Map<String, Object>> myStats();

    @GetMapping("/lessons/learn/progress")
    R<Map<String, Object>> courseProgress(@RequestParam("courseId") Long courseId);
}
