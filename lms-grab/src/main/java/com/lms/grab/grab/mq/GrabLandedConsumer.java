package com.lms.grab.grab.mq;

import cn.hutool.json.JSONUtil;
import com.lms.grab.grab.service.GrabRecordStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Receives the course-service acknowledgement after enrollment is persisted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabLandedConsumer {

    private final GrabRecordStateService stateService;

    @KafkaListener(topics = "${lms.grab.landed-topic:lms-grab-landed}", groupId = "lms-grab-landed")
    public void onLanded(String message) {
        Long recordId;
        try {
            recordId = JSONUtil.parseObj(message).getLong("recordId");
        } catch (Exception e) {
            log.warn("抢课落地回执格式非法，丢弃");
            return;
        }
        if (recordId == null) {
            log.warn("抢课落地回执缺少 recordId，丢弃");
            return;
        }
        boolean transitioned = stateService.markLanded(recordId);
        log.info("抢课落地回执处理完成 recordId={} transitioned={}", recordId, transitioned);
    }
}
