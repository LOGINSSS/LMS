package com.lms.course.course.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Python RAG 服务 Feign 契约（替代 lms-kb）
 *
 * 用途：建课后自动为课程创建知识库占位（单集合 + course_id 分区，幂等），
 * 供课程正文同步（ingest）/RAG 问答使用。直连 Python RAG 服务
 * （lms.rag.url，默认 http://127.0.0.1:13080），path=/rag 拼接出 /rag/kb 等端点。
 */
@FeignClient(name = "rag", contextId = "kbClient", path = "/rag",
        url = "${lms.rag.url:http://127.0.0.1:13080}", fallbackFactory = KbClientFallbackFactory.class)
public interface KbClient {

    /** 创建知识库（课程库：ownerType=1 + courseId） */
    @PostMapping("/kb")
    R<Long> createKb(@RequestBody Map<String, Object> body);

    /** 课程正文同步入知识库（服务间，mdText 为拼接的章节 markdown 全文） */
    @PostMapping("/kb/internal/courses/{courseId}/sync-text")
    R<Long> syncCourseText(@PathVariable("courseId") Long courseId, @RequestBody Map<String, String> body);

    /** 删除课程知识库（删除课程时清空该课程全部知识向量/文档；失败降级不阻断删课） */
    @DeleteMapping("/kb/courses/{courseId}")
    R<Map<String, Object>> deleteCourse(@PathVariable("courseId") Long courseId);
}
