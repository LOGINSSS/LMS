package com.lms.kb.rag.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 问答入参（spec §4.4：支持个人知识库 owner 维度）
 *
 * 兼容旧调用：只传 courseId 时按课程知识库检索（ownerType=1，ownerId=courseId）；
 * 个人知识库：ownerType=2 + ownerId=userId（或直接传 userId 由服务端补全 ownerType）。
 *
 * @param courseId  课程 id（ownerType=1 时必填）
 * @param question  用户问题
 * @param ownerType 归属类型：1 课程 / 2 用户，默认 1
 * @param ownerId   归属 id（ownerType=2 时为用户 id）
 */
public record RagRequest(
        Long courseId,
        @NotBlank(message = "请输入问题") String question,
        Integer ownerType,
        Long ownerId
) {
}
