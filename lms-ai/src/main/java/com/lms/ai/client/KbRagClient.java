package com.lms.ai.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * lms-kb 知识库服务 Feign 契约
 *
 * 转发课程知识库 RAG 问答（rewrite→HyDE→混合检索→精排→Qwen-VL 生成），
 * 走 Nacos 服务发现（lb://lms-kb），网关路由 /rag/** 已放行。
 */
@FeignClient(name = "lms-kb", contextId = "kbRagClient", path = "/rag")
public interface KbRagClient {

    @PostMapping("/chat")
    R<RagResponse> chat(@RequestBody RagChatRequest request);
}
