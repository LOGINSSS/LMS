package com.lms.ai.tools;

import com.lms.ai.client.KbRagClient;
import com.lms.ai.client.RagChatRequest;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识库工具组（spec §3.3：kb-agent 工具面）
 *
 * 注：L3 个人知识库已从 agent 记忆体系移除（两层记忆：L1 会话 + L2 ReMe 画像），
 * 本工具组只保留**课程知识库 RAG**（出题/答疑依据，复用 lms-kb 五步流水线）。
 */
@Component
@RequiredArgsConstructor
public class KbTools {

    private final KbRagClient ragClient;

    @Tool(name = "ragChatCourse", description = "RAG 问答（课程知识库）：基于指定课程的讲义/资料检索回答，返回答案与来源", readOnly = true)
    public String ragChatCourse(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "question", description = "问题") String question) {
        try {
            return ToolSupport.json(ToolSupport.check(ragClient.chat(RagChatRequest.forCourse(courseId, question))));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }
}
