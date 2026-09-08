package com.lms.ai.tools;

import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.harness.ToolMetaService;
import io.agentscope.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具元数据实现（P1，替换 DefaultToolMetaService）：启动扫描工具域 Bean 的 @Tool 注解
 *
 * - readOnly：直接取 @Tool.readOnly()（现有查询工具已标注）
 * - risk：默认 normal，覆盖表 lms.ai.harness-v2.tool-risk（toolId → high）
 * - toolId = 域前缀 + "." + @Tool name（如 exam.saveQuestion / im.pushMessage）
 * - 未知工具返回空（调用方 GuardedFunctionTool 只会为已注册工具创建，正常均能命中）
 */
@Slf4j
@Component
public class AnnotatedToolMetaService implements ToolMetaService {

    private final Map<String, ToolMeta> metaIndex = new ConcurrentHashMap<>();

    public AnnotatedToolMetaService(ToolDomainRegistry domainRegistry, HarnessV2Properties v2) {
        for (Map.Entry<String, Object> e : domainRegistry.domains().entrySet()) {
            String domain = e.getKey();
            for (Method m : e.getValue().getClass().getMethods()) {
                Tool ann = m.getAnnotation(Tool.class);
                if (ann == null) {
                    continue;
                }
                String toolId = domain + "." + ann.name();
                boolean readOnly = ann.readOnly();
                String risk = v2.getToolRisk() == null ? "normal"
                        : v2.getToolRisk().getOrDefault(toolId, "normal");
                metaIndex.put(toolId, new ToolMeta(readOnly, risk));
                log.debug("工具元数据注册: {} readOnly={} risk={}", toolId, readOnly, risk);
            }
        }
        log.info("工具元数据加载完成，共 {} 个", metaIndex.size());
    }

    @Override
    public Optional<ToolMeta> resolve(String toolId) {
        return Optional.ofNullable(metaIndex.get(toolId));
    }

    /** 全量元数据（调试/前端展示用） */
    public Map<String, ToolMeta> all() {
        return new HashMap<>(metaIndex);
    }
}
