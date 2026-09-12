package com.lms.ai.harness;

import java.util.Optional;

/**
 * 工具元数据源（spec HEAVY_HARNESS_SPEC §8.2 判定链第 7 步：风险分级）
 *
 * 元数据由启动扫描生成；无法解析的工具必须由 PolicyEngine fail-closed，禁止绕过工具治理。
 */
public interface ToolMetaService {

    /**
     * 按工具 id（domain.method，如 exam.saveQuestion）解析元数据
     *
     * @return 空 = 未注册/未知工具，调用方必须拒绝执行
     */
    Optional<ToolMeta> resolve(String toolId);

    /** 工具元数据：只读标记 + 风险等级（normal/high） */
    record ToolMeta(boolean readOnly, String risk) {
        public boolean isHighRisk() {
            return "high".equalsIgnoreCase(risk);
        }
    }
}
