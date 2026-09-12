package com.lms.ai.tools;

import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.config.AgentModelFactory;
import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.declaration.AgentDeclaration;
import com.lms.ai.harness.HarnessTraceService;
import com.lms.ai.harness.PolicyEngine;
import com.lms.ai.harness.ToolMetaService;
import com.lms.ai.registry.AgentRegistry;
import com.lms.ai.task.TaskService;
import io.agentscope.core.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolFactoryFailClosedTest {

    @Test
    void exposesOnlyToolsExplicitlyDeclaredByAgent() {
        ToolDomainRegistry domains = mock(ToolDomainRegistry.class);
        ToolMetaService metadata = mock(ToolMetaService.class);
        when(domains.bean("sample")).thenReturn(new SampleTools());
        when(metadata.resolve("sample.read"))
                .thenReturn(Optional.of(new ToolMetaService.ToolMeta(true, "normal")));
        ToolFactory factory = factory(domains, metadata);
        AgentDeclaration declaration = new AgentDeclaration();
        declaration.setName("test-agent");
        declaration.setTools(List.of("sample.read"));

        var toolkit = factory.build(declaration);

        assertThat(toolkit.getTool("read")).isNotNull();
        assertThat(toolkit.getTool("write")).isNull();
    }

    @Test
    void abortsToolkitBuildWhenDeclaredMethodIsMissing() {
        ToolDomainRegistry domains = mock(ToolDomainRegistry.class);
        when(domains.bean("sample")).thenReturn(new SampleTools());
        ToolFactory factory = factory(domains, mock(ToolMetaService.class));
        AgentDeclaration declaration = new AgentDeclaration();
        declaration.setName("test-agent");
        declaration.setTools(List.of("sample.missing"));

        assertThatThrownBy(() -> factory.build(declaration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("声明工具未注册")
                .hasMessageContaining("sample.missing");
    }

    @Test
    void abortsToolkitBuildWhenDeclaredDomainIsMissing() {
        ToolDomainRegistry domains = mock(ToolDomainRegistry.class);
        ToolFactory factory = factory(domains, mock(ToolMetaService.class));
        AgentDeclaration declaration = new AgentDeclaration();
        declaration.setName("test-agent");
        declaration.setTools(List.of("missing.execute"));

        assertThatThrownBy(() -> factory.build(declaration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具域未注册")
                .hasMessageContaining("missing");
    }

    @Test
    void abortsToolkitBuildWhenGuardWrappingFails() {
        ToolDomainRegistry domains = mock(ToolDomainRegistry.class);
        ToolMetaService metadata = mock(ToolMetaService.class);
        when(domains.bean("sample")).thenReturn(new SampleTools());
        when(metadata.resolve("sample.read")).thenThrow(new IllegalStateException("metadata unavailable"));

        ToolFactory factory = factory(domains, metadata);
        AgentDeclaration declaration = new AgentDeclaration();
        declaration.setName("test-agent");
        declaration.setTools(List.of("sample.read"));

        assertThatThrownBy(() -> factory.build(declaration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具网关壳化失败")
                .hasMessageContaining("sample.read");
    }

    @Test
    void abortsToolkitBuildWhenDeclaredDomainsExposeTheSameFunctionName() {
        ToolDomainRegistry domains = mock(ToolDomainRegistry.class);
        when(domains.bean("first")).thenReturn(new SampleTools());
        when(domains.bean("second")).thenReturn(new SampleTools());
        ToolFactory factory = factory(domains, mock(ToolMetaService.class));
        AgentDeclaration declaration = new AgentDeclaration();
        declaration.setName("test-agent");
        declaration.setTools(List.of("first.read", "second.read"));

        assertThatThrownBy(() -> factory.build(declaration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具名冲突")
                .hasMessageContaining("first.read")
                .hasMessageContaining("second.read");
    }

    private ToolFactory factory(ToolDomainRegistry domains, ToolMetaService metadata) {
        return new ToolFactory(
                mock(AgentRegistry.class),
                mock(AgentModelFactory.class),
                new AiAgentProperties(),
                new HarnessV2Properties(),
                mock(PolicyEngine.class),
                mock(HarnessTraceService.class),
                domains,
                mock(ToolControlService.class),
                metadata,
                mock(TaskService.class));
    }

    public static class SampleTools {
        @Tool(name = "read", description = "test tool", readOnly = true)
        public String read() {
            return "ok";
        }

        @Tool(name = "write", description = "undeclared test tool")
        public String write() {
            return "ok";
        }
    }
}
