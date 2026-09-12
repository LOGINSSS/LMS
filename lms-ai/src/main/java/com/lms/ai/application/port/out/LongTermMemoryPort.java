package com.lms.ai.application.port.out;

import java.util.List;

/** Application-owned boundary for recalling personal long-term memory. */
@FunctionalInterface
public interface LongTermMemoryPort {

    List<MemoryFragment> recall(RecallRequest request);

    record RecallRequest(
            Long userId,
            Integer userType,
            String agentType,
            String query,
            int limit) {
    }

    record MemoryFragment(String source, String content) {
    }
}
