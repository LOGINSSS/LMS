package com.lms.ai.infrastructure.ai.agentscope;

import com.lms.ai.application.port.out.AgentInvocation;
import com.lms.ai.application.port.out.AgentInvocationResult;
import com.lms.ai.application.port.out.AgentRuntime;
import com.lms.ai.factory.PersonalAgentFactory;
import com.lms.ai.harness.HarnessKeys;
import com.lms.ai.tools.ToolSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** AgentScope implementation of the application-owned personal-Agent runtime boundary. */
@Component
@RequiredArgsConstructor
public class AgentScopeRuntimeAdapter implements AgentRuntime {

    private final PersonalAgentFactory personalAgentFactory;

    @Override
    public AgentInvocationResult invoke(AgentInvocation invocation) {
        ReActAgent agent = personalAgentFactory.build(
                invocation.userId(),
                invocation.userType(),
                invocation.agentType(),
                invocation.intentHint(),
                invocation.longTermMemoryContext());
        try {
            RuntimeContext context = runtimeContext(invocation);
            Msg result = agent.call(messages(invocation), context).block();
            return new AgentInvocationResult(result == null ? null : result.getTextContent());
        } finally {
            agent.close();
        }
    }

    private List<Msg> messages(AgentInvocation invocation) {
        return invocation.messages().stream()
                .map(message -> switch (message.role()) {
                    case USER -> (Msg) new UserMessage(message.text());
                    case ASSISTANT -> new AssistantMessage(message.text());
                })
                .toList();
    }

    private RuntimeContext runtimeContext(AgentInvocation invocation) {
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(String.valueOf(invocation.sessionId()))
                .userId(String.valueOf(invocation.userId()))
                .put(ToolSupport.CTX_USER_ID, invocation.userId())
                .put(ToolSupport.CTX_USER_TYPE, invocation.userType())
                .build();
        if (invocation.intent() != null && !invocation.intent().isBlank()) {
            context.put(HarnessKeys.CTX_INTENT, invocation.intent());
        }
        return context;
    }
}
