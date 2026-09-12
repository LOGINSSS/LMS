package com.lms.ai.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolDomainRegistryTest {

    @Test
    void exposesGrabDomainDeclaredByCourseAgent() {
        GrabTools grab = mock(GrabTools.class);
        ToolDomainRegistry registry = new ToolDomainRegistry(
                mock(CourseTools.class), grab, mock(ExamTools.class),
                mock(LearningTools.class), mock(SearchTools.class), mock(RemarkTools.class),
                mock(UserTools.class), mock(MediaTools.class), mock(StatisticsTools.class),
                mock(KbTools.class), mock(ImTools.class), mock(TaskTools.class),
                mock(EvalTools.class), mock(LearningPipelineTools.class));

        assertThat(registry.bean("grab")).isSameAs(grab);
        assertThat(registry.domains()).containsEntry("grab", grab).hasSize(14);
    }
}
