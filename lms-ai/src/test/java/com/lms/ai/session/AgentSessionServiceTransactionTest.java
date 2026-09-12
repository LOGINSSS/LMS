package com.lms.ai.session;

import com.lms.ai.config.AiAgentProperties;
import com.lms.ai.harness.HarnessSessionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionServiceTransactionTest {

    @Test
    void shortTermMessagesAreRemovedOnlyAfterDatabaseCommit() {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HarnessSessionGuard guard = mock(HarnessSessionGuard.class);
        AgentSession session = new AgentSession();
        session.setId(99L);
        session.setUserId(7L);
        session.setStatus(AgentSession.STATUS_ACTIVE);
        when(mapper.selectById(99L)).thenReturn(session);
        AgentSessionService service = new AgentSessionService(mapper, redis, new AiAgentProperties(), guard);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.closeSession(7L, 99L);
            verify(redis, never()).delete(anyString());
            verify(guard, never()).clear(99L);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(redis, times(2)).delete(anyString());
            verify(guard).clear(99L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
