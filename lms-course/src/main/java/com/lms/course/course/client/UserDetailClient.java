package com.lms.course.course.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * lms-user 档案明细 Feign（选课资格判定：学生年级/专业/学院，按档案 id 查询）
 *
 * 返回 map 容错解析（字段缺失按未满足处理），调用方捕获异常降级。
 */
@FeignClient(name = "lms-user", contextId = "courseUserDetailClient")
public interface UserDetailClient {

    @GetMapping("/users/{id}")
    R<Map<String, Object>> queryUserById(@PathVariable("id") Long id);
}
