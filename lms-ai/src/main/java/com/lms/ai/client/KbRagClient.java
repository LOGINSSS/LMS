package com.lms.ai.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Python RAG 服务契约（替代 lms-kb）
 *
 * 转发课程知识库 RAG 问答（rewrite→HyDE→混合检索→精排→Qwen-VL 生成），
 * 直连 Python RAG 服务（FastAPI，端口见 lms.rag.url，默认 http://127.0.0.1:13080）。
 */
@FeignClient(name = "rag", contextId = "kbRagClient", url = "${lms.rag.url:http://127.0.0.1:13080}", path = "/rag")
public interface KbRagClient {

    @PostMapping("/chat")
    R<RagResponse> chat(@RequestBody RagChatRequest request);
}
