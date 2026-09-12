package com.lms.ai.harness;

import com.lms.ai.config.HarnessProperties;
import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.registry.AgentRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyEngineFailClosedTest {

    @Test
    void deniesToolWhenMetadataIsMissing() {
        ToolMetaService metadata = mock(ToolMetaService.class);
        when(metadata.resolve("unknown.execute")).thenReturn(Optional.empty());
        PolicyEngine engine = new PolicyEngine(
                mock(AgentRegistry.class),
                new HarnessProperties(),
                new HarnessV2Properties(),
                mock(HitlService.class),
                metadata,
                mock(PipelineTokenService.class),
                mock(HarnessSessionGuard.class));

        PolicyDecision decision = engine.decideTool(new PolicyEngine.ToolCtx(
                "99", 7L, 1, "student-agent", "unknown.execute", Map.of(), null));

        assertThat(decision.verdict()).isEqualTo(PolicyDecision.Verdict.DENY);
        assertThat(decision.code()).isEqualTo("UNKNOWN_TOOL");
    }
}
