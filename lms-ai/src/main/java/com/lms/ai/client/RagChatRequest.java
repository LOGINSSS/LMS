package com.lms.ai.client;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 问答入参（转发 lms-kb /rag/chat 的契约，spec §4.4：支持个人知识库 owner 维度）
 *
 * @param courseId  课程 id（ownerType=1 课程库时必填）
 * @param question  用户问题
 * @param ownerType 归属类型：1 课程 / 2 用户，默认 1
 * @param ownerId   归属 id（ownerType=2 时为用户 id）
 */
public record RagChatRequest(
        Long courseId,
        @NotBlank(message = "请输入问题") String question,
        Integer ownerType,
        Long ownerId
) {

    /** 课程库快捷构造（L3 个人知识库已从记忆体系移除，agent 侧只走课程库） */
    public static RagChatRequest forCourse(Long courseId, String question) {
        return new RagChatRequest(courseId, question, 1, courseId);
    }
}
