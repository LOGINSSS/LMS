package com.lms.ai.application.context;

import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.llm.ModelCaller;
import com.lms.ai.session.AgentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话上下文压缩器：达到阈值后将较早消息压缩为摘要，并保留最近消息。
 * 自动摘要默认关闭；模型失败时回退为纯裁剪，不阻塞主对话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressor {

    private final AgentSessionService sessionService;
    private final ModelCaller modelCaller;
    private final HarnessV2Properties v2;

    @Value("${lms.ai.agent.summary-model:qwen-turbo}")
    private String summaryModel;

    public boolean compressIfNeeded(Long sessionId) {
        if (!v2.isSummaryEnabled() || sessionId == null) {
            return false;
        }
        List<Map<String, String>> messages = sessionService.loadMessagesRaw(sessionId);
        int threshold = v2.getSummaryThreshold();
        if (threshold <= 0 || messages.size() < threshold) {
            return false;
        }
        if (sessionService.getSummary(sessionId) != null) {
            return false;
        }
        int keep = v2.getSummaryKeep() > 0 ? v2.getSummaryKeep() : threshold / 2;
        List<Map<String, String>> compressPart = messages.subList(0, messages.size() - keep);
        int dropCount = compressPart.size();
        if (dropCount <= 1) {
            return false;
        }
        String joined = compressPart.stream()
                .map(message -> ("user".equals(message.get("role")) ? "用户：" : "助手：")
                        + message.get("text"))
                .collect(Collectors.joining("\n"));
        String prompt = "请把以下 LMS 学习助手与用户的早期对话压缩成不超过 150 字的中文摘要，"
                + "保留：用户身份相关事实、已解决问题结论、进行中/未完成任务与待办、引用过的关键数据/题目/课程。"
                + "不要编造。\n\n" + joined;
        String summary = modelCaller.chat(summaryModel, null, prompt, 512);
        if (summary == null || summary.isBlank()) {
            log.warn("会话摘要生成失败，回退纯裁剪 sessionId={}", sessionId);
            sessionService.trimToKeep(sessionId, keep);
            return true;
        }
        sessionService.putSummary(sessionId, summary);
        sessionService.trimToKeep(sessionId, keep);
        log.info("会话摘要压缩完成 sessionId={} drop={} keep={}", sessionId, dropCount, keep);
        return true;
    }
}
