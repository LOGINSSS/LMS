package com.lms.ai.controller;

import com.lms.ai.session.AgentSessionService;
import com.lms.ai.task.AgentTask;
import com.lms.ai.task.TaskService;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.exceptions.UnauthorizedException;
import com.lms.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskControllerAuthorizationTest {

    private final TaskService tasks = mock(TaskService.class);
    private final AgentSessionService sessions = mock(AgentSessionService.class);
    private final TaskController controller = new TaskController(tasks, sessions);

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void unauthenticatedUserCannotReadTasks() {
        assertThatThrownBy(() -> controller.query("task-1"))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    void unauthenticatedUserCannotReadTaskTree() {
        assertThatThrownBy(() -> controller.tree(9L))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(tasks, sessions);
    }

    @Test
    void studentCannotPublishAdministrativeTask() {
        UserContext.setUser(7L);
        UserContext.setUserType(1);

        TaskController.TaskScheduleRequest request = new TaskController.TaskScheduleRequest(
                "qa_remind", AgentTask.TRIGGER_DELAY, Map.of(), 60, null);
        assertThatThrownBy(() -> controller.schedule(request))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    void teacherPublishesTaskAsThemselves() {
        UserContext.setUser(8L);
        UserContext.setUserType(2);
        when(tasks.schedule("qa_remind", 8L, "teacher-agent",
                AgentTask.TRIGGER_DELAY, Map.of(), 60, null)).thenReturn("task-1");

        controller.schedule(new TaskController.TaskScheduleRequest(
                "qa_remind", AgentTask.TRIGGER_DELAY, Map.of(), 60, null));

        verify(tasks).schedule("qa_remind", 8L, "teacher-agent",
                AgentTask.TRIGGER_DELAY, Map.of(), 60, null);
    }

    @Test
    void queryAndCancelAreScopedToCurrentOwner() {
        UserContext.setUser(7L);
        UserContext.setUserType(1);
        when(tasks.queryOwned("task-1", 7L)).thenReturn(new AgentTask());

        controller.query("task-1");
        controller.cancel("task-1");

        verify(tasks).queryOwned("task-1", 7L);
        verify(tasks).cancelOwned("task-1", 7L);
    }
}
