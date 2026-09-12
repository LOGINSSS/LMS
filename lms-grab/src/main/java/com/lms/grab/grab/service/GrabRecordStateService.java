package com.lms.grab.grab.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lms.grab.grab.domain.po.GrabRecord;
import com.lms.grab.grab.enums.GrabRecordStatus;
import com.lms.grab.grab.mapper.GrabRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Applies idempotent state transitions to the grab audit record. */
@Service
@RequiredArgsConstructor
public class GrabRecordStateService {

    private final GrabRecordMapper mapper;

    /** A duplicate landed receipt is a no-op; refunded records can never be revived. */
    public boolean markLanded(Long recordId) {
        if (recordId == null) {
            return false;
        }
        int updated = mapper.update(null, new UpdateWrapper<GrabRecord>()
                .eq("id", recordId)
                .eq("status", GrabRecordStatus.PENDING.getValue())
                .set("status", GrabRecordStatus.LANDED.getValue()));
        return updated == 1;
    }
}
