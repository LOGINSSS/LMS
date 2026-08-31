package com.lms.course.course.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * lms-kb 知识库服务 Feign 契约（spec 0.2 §8.1：课程创建自动建库）
 *
 * 用途：建课后自动创建课程知识库（ownerType=1 + courseId，幂等），
 * 供课程正文同步/RAG 问答使用。
 */
@FeignClient(name = "lms-kb", contextId = "kbClient", fallbackFactory = KbClientFallbackFactory.class)
public interface KbClient {

    /** 创建知识库（课程库：ownerType=1 + courseId） */
    @PostMapping("/kb")
    R<Long> createKb(@RequestBody Map<String, Object> body);

    /** 课程正文同步入知识库（服务间，mdText 为拼接的章节 markdown 全文） */
    @PostMapping("/kb/internal/courses/{courseId}/sync-text")
    R<Long> syncCourseText(@PathVariable("courseId") Long courseId, @RequestBody Map<String, String> body);
}
