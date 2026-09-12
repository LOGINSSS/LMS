package com.lms.ai.infrastructure.memory;

import com.lms.ai.application.port.out.LongTermMemoryPort;
import com.lms.ai.memory.ProfileService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileMemoryAdapterTest {

    @Test
    void exposesProfileAsApplicationOwnedMemoryFragment() {
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.profileInjection(7L)).thenReturn("学习偏好：示例优先");
        ProfileMemoryAdapter adapter = new ProfileMemoryAdapter(profiles);

        var fragments = adapter.recall(new LongTermMemoryPort.RecallRequest(
                7L, 1, "student-agent", "如何学习？", 5));

        assertThat(fragments).containsExactly(
                new LongTermMemoryPort.MemoryFragment("profile", "学习偏好：示例优先"));
    }

    @Test
    void omitsBlankProfileMemory() {
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.profileInjection(7L)).thenReturn("  ");
        ProfileMemoryAdapter adapter = new ProfileMemoryAdapter(profiles);

        assertThat(adapter.recall(new LongTermMemoryPort.RecallRequest(
                7L, 1, "student-agent", "问题", 5))).isEmpty();
    }
}
