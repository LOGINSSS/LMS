package com.lms.ai.controller;

import com.lms.ai.chat.AgentChatService;
import com.lms.ai.chat.AgentStreamService;
import com.lms.ai.application.memory.PersonalWikiService;
import com.lms.ai.application.memory.SessionClosureService;
import com.lms.ai.memory.ProfileService;
import com.lms.ai.orchestration.OrchestratorService;
import com.lms.ai.session.AgentSessionService;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentControllerAuthorizationTest {

    private final OrchestratorService orchestrator = mock(OrchestratorService.class);
    private final AgentController controller = new AgentController(
            mock(AgentChatService.class), mock(AgentSessionService.class),
            mock(ProfileService.class), orchestrator, mock(AgentStreamService.class),
            mock(PersonalWikiService.class), mock(SessionClosureService.class));

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void studentCannotInvokeTeacherExamGeneration() {
        UserContext.setUser(7L);
        UserContext.setUserType(1);

        assertThatThrownBy(() -> controller.generateExam(
                new AgentController.GenerateRequest(null, "生成期中试卷")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void studentCannotInvokeTeacherCourseGeneration() {
        UserContext.setUser(7L);
        UserContext.setUserType(1);

        assertThatThrownBy(() -> controller.generateCourse(
                new AgentController.GenerateRequest(null, "创建一门课程")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void studentCannotSelectTeacherAgentThroughGenericChat() {
        UserContext.setUser(7L);
        UserContext.setUserType(1);

        assertThatThrownBy(() -> controller.chat(
                new AgentController.ChatRequest(null, "替我建课", "teacher-agent")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentCannotCreateTeacherAgentSession() {
        UserContext.setUser(7L);
        UserContext.setUserType(1);

        assertThatThrownBy(() -> controller.createSession(
                new AgentController.CreateSessionRequest("teacher", "越权会话")))
                .isInstanceOf(ForbiddenException.class);
    }
}
