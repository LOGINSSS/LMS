package com.lms.grab.grab.task;

import com.lms.grab.grab.domain.po.GrabRecord;
import com.lms.grab.grab.enums.GrabRecordStatus;
import com.lms.grab.grab.mapper.GrabRecordMapper;
import com.lms.grab.grab.service.GrabEventProducer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrabReconcileSchedulerTest {

    @Test
    void oldPendingRecordMustBeRetriedInsteadOfRefundedBeforeAuthoritativeReconciliation() {
        GrabRecordMapper mapper = mock(GrabRecordMapper.class);
        GrabEventProducer producer = mock(GrabEventProducer.class);
        GrabRecord record = new GrabRecord();
        record.setId(8L);
        record.setCourseId(11L);
        record.setUserId(22L);
        record.setGrabTime(LocalDateTime.now().minusMinutes(20));
        record.setStatus(GrabRecordStatus.PENDING.getValue());
        when(mapper.selectList(any())).thenReturn(List.of(record));

        new GrabReconcileScheduler(mapper, producer).reconcile();

        verify(producer).publishSuccess(eq(11L), eq(22L), anyString(), eq(8L));
        verify(mapper, never()).update(any(), any());
    }
}
