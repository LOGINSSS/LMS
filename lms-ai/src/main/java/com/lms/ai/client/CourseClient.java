package com.lms.ai.client;

import com.lms.common.domain.R;
import com.lms.common.domain.dto.PageDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * lms-course 课程服务 Feign 契约（spec §3.3：course-agent 工具面）
 *
 * 请求体字段与 CourseFormDTO 一致：name(必填)/cover/intro/category；
 * 需要登录/教师身份的接口由 Feign 拦截器补 user-info 头（见 AgentConfig）。
 */
@FeignClient(name = "lms-course", contextId = "courseClient")
public interface CourseClient {

    @PostMapping("/admin/courses")
    R<Long> addCourse(@RequestBody Map<String, Object> body);

    @PutMapping("/admin/courses/{id}")
    R<Void> updateCourse(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PutMapping("/admin/courses/{id}/status")
    R<Void> changeStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    @GetMapping("/admin/courses/mine")
    R<PageDTO<Object>> myCourses(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                 @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/courses/page")
    R<PageDTO<Object>> pageCourses(@RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam(value = "category", required = false) String category,
                                   @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                   @RequestParam(value = "pageSize", required = false) Integer pageSize);

    @GetMapping("/courses/{id}")
    R<Object> getDetail(@PathVariable("id") Long id);

    @PostMapping("/courses/{id}/enroll")
    R<Void> enroll(@PathVariable("id") Long id);

    @PostMapping("/courses/{id}/quit")
    R<Void> quit(@PathVariable("id") Long id);

    @GetMapping("/courses/enrolled")
    R<PageDTO<Object>> enrolled(@RequestParam(value = "pageNo", required = false) Integer pageNo,
                                @RequestParam(value = "pageSize", required = false) Integer pageSize);
}
