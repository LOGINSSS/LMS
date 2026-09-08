package com.lms.ai.tools;

import cn.hutool.core.util.StrUtil;
import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.harness.HarnessKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 工具运行时开关（spec HEAVY_HARNESS_SPEC §7.5：静态配置 + Redis 动态键）
 *
 * - 静态：lms.ai.harness-v2.tool-switch-off（工具级名单，重启生效）
 * - 动态：Redis 键 tool:switch:{toolId} 值为 off/on（运维关停坏工具/灰度，不重启）
 * - Redis 异常 fail-open（可用性优先，仅告警；真正的安全边界在 PolicyEngine 角色矩阵/风险分级）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolControlService {

    private final HarnessV2Properties v2;
    private final StringRedisTemplate redisTemplate;

    /**
     * 校验工具是否开启；返回拒绝原因（空 = 放行）
     */
    public Optional<String> disabledReason(String toolId) {
        if (toolId == null) {
            return Optional.of("工具标识为空");
        }
        // 静态名单
        if (v2.getToolSwitchOff() != null && v2.getToolSwitchOff().contains(toolId)) {
            return Optional.of("Harness 拦截：工具 " + toolId + " 已被静态关停，暂时不可用，请重新规划。");
        }
        // 动态 Redis 键
        try {
            String v = redisTemplate.opsForValue().get(HarnessKeys.TOOL_SWITCH_PREFIX + toolId);
            if (StrUtil.isNotBlank(v) && ("off".equalsIgnoreCase(v) || "0".equalsIgnoreCase(v))) {
                return Optional.of("Harness 拦截：工具 " + toolId + " 当前被动态关停，暂时不可用，请重新规划。");
            }
        } catch (Exception e) {
            log.warn("ToolControl Redis 读取失败 toolId={}（fail-open）: {}", toolId, e.getMessage());
        }
        return Optional.empty();
    }
}
