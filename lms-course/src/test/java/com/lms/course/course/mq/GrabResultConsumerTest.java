package com.lms.course.course.mq;

import com.lms.course.course.mapper.CourseEnrollmentMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GrabResultConsumerTest {

    private final CourseEnrollmentMapper mapper = mock(CourseEnrollmentMapper.class);
    private final GrabLandedProducer producer = mock(GrabLandedProducer.class);
    private final GrabResultConsumer consumer = new GrabResultConsumer(mapper, producer);

    @Test
    void publishesReceiptForCanonicalRecordId() {
        consumer.onGrabSuccess("{\"courseId\":11,\"userId\":22,\"recordId\":33}");

        verify(producer).publish(33L, 11L, 22L);
    }

    @Test
    void acceptsLegacyGrabRecordIdDuringRollingUpgrade() {
        consumer.onGrabSuccess("{\"courseId\":11,\"userId\":22,\"grabRecordId\":33}");

        verify(producer).publish(33L, 11L, 22L);
    }
}
