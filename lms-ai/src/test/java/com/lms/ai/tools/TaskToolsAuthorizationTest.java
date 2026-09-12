package com.lms.ai.tools;

import com.lms.ai.task.AgentTask;
import com.lms.ai.task.TaskService;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskToolsAuthorizationTest {

    @Test
    void queryWithoutRuntimeIdentityIsRejectedBeforeTaskLookup() {
        TaskService service = mock(TaskService.class);
        TaskTools tools = new TaskTools(service);

        assertThat(tools.query("task-1", RuntimeContext.builder().build()))
                .contains("需要登录");
        verifyNoInteractions(service);
    }

    @Test
    void queryAndCancelUseIdentityFromRuntimeContext() {
        TaskService service = mock(TaskService.class);
        AgentTask task = new AgentTask();
        task.setTaskId("task-1");
        task.setTaskType("qa_remind");
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setExecuteTime(java.time.LocalDateTime.now());
        when(service.queryOwned("task-1", 7L)).thenReturn(task);
        TaskTools tools = new TaskTools(service);
        RuntimeContext context = RuntimeContext.builder()
                .userId("7")
                .put(ToolSupport.CTX_USER_ID, 7L)
                .put(ToolSupport.CTX_USER_TYPE, 1)
                .build();

        assertThat(tools.query("task-1", context)).contains("task-1");
        assertThat(tools.cancel("task-1", context)).isEqualTo("ok");

        verify(service).queryOwned("task-1", 7L);
        verify(service).cancelOwned("task-1", 7L);
    }
}
