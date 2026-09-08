package com.lms.exam.exam.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * lms-learning 学习数据中心 Feign 契约（作答记录回流：作业/考试判分结果 → 做题记录）
 *
 * 由 UserInfoFeignConfig 补 user-info 头（作答学生身份），lms-learning 侧按 UserContext 归属。
 */
@FeignClient(name = "lms-learning", contextId = "examLearningRecordClient", path = "/learn")
public interface LearningRecordClient {

    @PostMapping("/exercises")
    R<Long> recordExercise(@RequestBody Map<String, Object> body);
}
