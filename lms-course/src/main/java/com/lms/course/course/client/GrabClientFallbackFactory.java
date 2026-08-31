package com.lms.course.course.client;

import com.lms.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * lms-grab 预热失败降级：记录日志，不阻断课程发布主流程
 * （抢课窗口打开时 lms-grab 侧若未预热，抢课会报"尚未开放"，由对账/重试兜底）。
 */
@Slf4j
@Component
public class GrabClientFallbackFactory implements FallbackFactory<GrabClient> {

    @Override
    public GrabClient create(Throwable cause) {
        return new GrabClient() {
            @Override
            public R<Void> prepare(Long courseId, String grabStartTime, String grabEndTime, Integer stock) {
                log.warn("调用 lms-grab 预热库存失败 courseId={}: {}", courseId, cause.getMessage());
                return R.fail("抢课库存预热失败");
            }
        };
    }
}
