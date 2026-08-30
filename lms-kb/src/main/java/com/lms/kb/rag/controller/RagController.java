package com.lms.kb.rag.controller;

import com.lms.common.domain.R;
import com.lms.kb.rag.RagService;
import com.lms.kb.rag.domain.RagRequest;
import com.lms.kb.rag.domain.RagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答接口（对应 Python 版 graph.py 的 run_rag）
 */
@Tag(name = "RAG 问答接口")
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/chat")
    @Operation(summary = "课程知识库 RAG 问答（rewrite→HyDE→混合检索→精排→生成）")
    public R<RagResponse> chat(@RequestBody @Valid RagRequest request) {
        return R.ok(ragService.rag(request));
    }
}
