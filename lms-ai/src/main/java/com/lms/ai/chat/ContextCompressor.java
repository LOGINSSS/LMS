package com.lms.ai.chat;

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
 * 上下文压缩器（P4：LLM 摘要压缩，spec HEAVY_HARNESS_SPEC §5.5）
 *
 * 触发：消息条数 ≥ summary-threshold 且该会话尚无摘要时，把最旧一段用 qwen-turbo 压成摘要
 * （结论/待办/未完成任务），写入 Redis 摘要 key（loadMessages 置顶注入），消息只保留最近 keep 条；
 * 后续轮次消息再次涨到阈值且已有摘要 → 摘要已置顶，正常继续（待后续轮按需再压）。
 * LLM 失败 → 回退纯裁剪（trimToKeep），不阻塞对话；summary-enabled=false 时不做任何事（现状行为）。
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

    /**
     * 压缩判定与执行（best-effort，调用方 try/catch 兜底）
     *
     * @return 是否发生了压缩
     */
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
            return false; // 本会话已有摘要，避免每轮重复压缩
        }
        int keep = v2.getSummaryKeep() > 0 ? v2.getSummaryKeep() : threshold / 2;
        List<Map<String, String>> compressPart = messages.subList(0, messages.size() - keep);
        int dropCount = compressPart.size();
        if (dropCount <= 1) {
            return false;
        }
        String joined = compressPart.stream()
                .map(m -> ("user".equals(m.get("role")) ? "用户：" : "助手：") + m.get("text"))
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
