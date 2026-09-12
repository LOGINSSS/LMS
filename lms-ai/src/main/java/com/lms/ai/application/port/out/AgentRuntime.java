package com.lms.ai.application.port.out;

/** Executes one personal-Agent invocation without exposing a vendor runtime to the application layer. */
public interface AgentRuntime {

    AgentInvocationResult invoke(AgentInvocation invocation);
}
