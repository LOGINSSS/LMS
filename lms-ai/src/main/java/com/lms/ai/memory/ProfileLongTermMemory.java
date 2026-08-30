package com.lms.ai.memory;

import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * L2 画像长期记忆实现（spec §4.1：LongTermMemory 映射）
 *
 * - record(对话消息列表)：对话消息 → 行为事件流水（event_type=chat，payload=问题文本）
 * - retrieve(Msg)：返回画像摘要/兴趣/习惯注入文本（回答前注入）
 * 写权限分级（spec §4.1）：画像只由行为事件 + 显式"记住"写入，防 agent 幻觉污染。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileLongTermMemory implements LongTermMemory {

    private final ProfileService profileService;

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        // LongTermMemory 契约无用户上下文；行为流水由 AgentChatService 在对话流程中
        // 以 (userId, event_type=chat, question) 显式写入 ProfileService.recordBehavior
        // （spec §4.3：L2 只由行为事件 + 显式"记住"写入，防幻觉污染）。
        return Mono.empty();
    }

    @Override
    public Mono<String> retrieve(Msg query) {
        // 无用户上下文时返回空；真正的画像注入由 PersonalAgentFactory 构建时完成（有 userId）
        return Mono.just("");
    }

    /** 带用户上下文的画像检索（PersonalAgentFactory 构建 agent 时调用） */
    public String retrieveFor(Long userId) {
        return profileService.profileInjection(userId);
    }
}
