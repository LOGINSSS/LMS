package com.lms.ai.declaration;

import lombok.Data;

/**
 * 三层记忆声明（spec §4.1：L1 会话 / L2 画像 / L3 知识库）
 */
@Data
public class MemoryDeclaration {

    /** layer1 会话记忆：session（默认开启） */
    private String layer1 = "session";

    /** layer2 画像记忆：profile（只读注入画像摘要） */
    private String layer2 = "profile";

    /** layer3 知识文档记忆：kb（按需检索） */
    private String layer3 = "kb";
}
