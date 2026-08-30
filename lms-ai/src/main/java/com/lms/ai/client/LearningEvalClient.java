package com.lms.ai.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * lms-learning 学习数据中心 Feign 契约（评测业务线：诊断→规划→习题→测评闭环）
 *
 * 需求文档 §4.7：做题记录/错题集、测评报告、学情聚合（诊断智能体输入）。
 * 需要登录身份的接口由 Feign 拦截器按 AgentContextHolder 补 user-info 头。
 */
@FeignClient(name = "lms-learning", contextId = "learningEvalClient")
public interface LearningEvalClient {

    @PostMapping("/learn/exercises")
    R<Long> recordExercise(@RequestBody Map<String, Object> body);

    @GetMapping("/learn/exercises/mine")
    R<PageDTO<Object>> myExercises(@RequestParam(value = "courseId", required = false) Long courseId,
                                   @RequestParam(value = "onlyWrong", required = false) Boolean onlyWrong,
                                   @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                   @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @PostMapping("/learn/assessments")
    R<Long> saveAssessment(@RequestBody Map<String, Object> body);

    @GetMapping("/learn/assessments/mine")
    R<List<Object>> myAssessments(@RequestParam(value = "courseId", required = false) Long courseId);

    @GetMapping("/learn/stats/diagnosis")
    R<Object> diagnosis();
}
