package com.lms.ai.task;

import com.lms.ai.im.ImService;
import com.lms.common.exceptions.CommonException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceOwnershipTest {

    @Test
    void queryReturnsTaskOwnedByCurrentUser() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        AgentTask owned = new AgentTask();
        owned.setTaskId("task-1");
        owned.setOwnerId(7L);
        when(mapper.selectOne(any())).thenReturn(owned);
        TaskService service = new TaskService(mapper, mock(ImService.class), mock(TaskEventProducer.class));

        assertThat(service.queryOwned("task-1", 7L)).isSameAs(owned);
    }

    @Test
    void queryRejectsTaskOwnedByAnotherUser() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        AgentTask anotherUsersTask = new AgentTask();
        anotherUsersTask.setTaskId("task-1");
        anotherUsersTask.setOwnerId(8L);
        when(mapper.selectOne(any())).thenReturn(anotherUsersTask);
        TaskService service = new TaskService(mapper, mock(ImService.class), mock(TaskEventProducer.class));

        assertThatThrownBy(() -> service.queryOwned("task-1", 7L))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void cancelRejectsTaskNotOwnedByCurrentUser() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        TaskService service = new TaskService(mapper, mock(ImService.class), mock(TaskEventProducer.class));

        assertThatThrownBy(() -> service.cancelOwned("task-1", 7L))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("无权访问");
        verify(mapper, never()).update(any(), any());
    }
}
