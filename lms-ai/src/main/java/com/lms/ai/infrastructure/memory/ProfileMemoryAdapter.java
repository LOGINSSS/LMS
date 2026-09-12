package com.lms.ai.infrastructure.memory;

import com.lms.ai.application.port.out.LongTermMemoryPort;
import com.lms.ai.memory.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Current MySQL-profile implementation of the application long-term-memory boundary. */
@Component
@RequiredArgsConstructor
public class ProfileMemoryAdapter implements LongTermMemoryPort {

    private final ProfileService profileService;

    @Override
    public List<MemoryFragment> recall(RecallRequest request) {
        String profile = profileService.profileInjection(request.userId());
        if (profile == null || profile.isBlank()) {
            return List.of();
        }
        return List.of(new MemoryFragment("profile", profile));
    }
}
