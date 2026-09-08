package com.lms.ai.harness;

import java.util.Optional;

/**
 * 工具元数据源（spec HEAVY_HARNESS_SPEC §8.2 判定链第 7 步：风险分级）
 *
 * P0 阶段工具仍是直注 Bean，尚无原子注册表 → 默认返回 empty（未知工具按 normal 放行，仅角色矩阵生效）；
 * P1 接入 ToolRegistry（@LmsTool 注解扫描）后，本接口由注册表实现，PolicyEngine 无需改动。
 */
public interface ToolMetaService {

    /**
     * 按工具 id（domain.method，如 exam.saveQuestion）解析元数据
     *
     * @return 空 = 未注册/未知工具（P0 阶段兜底；P1 起由调用方 ToolGateway 在注册表查不到时先行 DENY）
     */
    Optional<ToolMeta> resolve(String toolId);

    /** 工具元数据：只读标记 + 风险等级（normal/high） */
    record ToolMeta(boolean readOnly, String risk) {
        public boolean isHighRisk() {
            return "high".equalsIgnoreCase(risk);
        }
    }
}
