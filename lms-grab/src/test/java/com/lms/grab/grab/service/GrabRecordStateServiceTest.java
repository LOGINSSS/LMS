package com.lms.grab.grab.service;

import com.lms.grab.grab.mapper.GrabRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrabRecordStateServiceTest {

    @Mock
    private GrabRecordMapper mapper;

    @Test
    void landedReceiptOnlyTransitionsPendingRecord() {
        when(mapper.update(isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        GrabRecordStateService service = new GrabRecordStateService(mapper);

        boolean transitioned = service.markLanded(42L);

        assertThat(transitioned).isTrue();
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> wrapper =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertThat(sql).contains("id").contains("status");
    }

    @Test
    void duplicateLandedReceiptIsIdempotent() {
        when(mapper.update(isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        GrabRecordStateService service = new GrabRecordStateService(mapper);

        assertThat(service.markLanded(42L)).isFalse();
    }
}
