package com.lms.ai.registry;

import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.declaration.AgentDeclaration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 注册表（spec §3.2：启动时扫描 classpath agents/*.yaml 解析为 name → AgentDeclaration 映射）
 *
 * 存放：lms-ai/src/main/resources/agents/*.yaml（练手期 classpath；后续可演进 agent_registry 表）。
 * 个人 agent（teacher/student）由 PersonalAgentFactory 按 user_id 实例化，子 agent 声明在此集中注册。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private final AiAgentProperties properties;

    /** name → 声明（不可变快照） */
    private Map<String, AgentDeclaration> declarations = Collections.emptyMap();

    @PostConstruct
    public void load() {
        Map<String, AgentDeclaration> map = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + properties.getDeclarationPath() + "/*.yaml");
            // SnakeYAML 2.x：Constructor(Class) 已移除，需显式 LoaderOptions
            org.yaml.snakeyaml.LoaderOptions loaderOptions = new org.yaml.snakeyaml.LoaderOptions();
            Yaml yaml = new Yaml(new org.yaml.snakeyaml.constructor.Constructor(AgentDeclaration.class, loaderOptions));
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    AgentDeclaration d = yaml.load(in);
                    if (d == null || d.getName() == null || d.getName().isBlank()) {
                        log.warn("忽略无效 Agent 声明: {}", r.getFilename());
                        continue;
                    }
                    if (map.containsKey(d.getName())) {
                        throw new IllegalStateException("Agent 声明重名: " + d.getName());
                    }
                    map.put(d.getName(), d);
                    log.debug("注册 Agent 声明: {} (role={}, tools={}, subAgents={})",
                            d.getName(), d.getRole(), d.getTools().size(), d.getSubAgents().size());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 Agent 声明失败", e);
        }
        declarations = Map.copyOf(map);
        log.info("Agent 注册表加载完成，共 {} 个声明: {}", declarations.size(), declarations.keySet());
    }

    public AgentDeclaration get(String name) {
        AgentDeclaration d = declarations.get(name);
        if (d == null) {
            throw new IllegalArgumentException("Agent 声明不存在: " + name);
        }
        return d;
    }

    public boolean contains(String name) {
        return declarations.containsKey(name);
    }

    public Map<String, AgentDeclaration> all() {
        return declarations;
    }
}
