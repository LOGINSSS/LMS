package com.lms.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReMePropertiesTest {

    @Test
    void enabledConfigurationRequiresPerUserEndpoint() {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(true);
        properties.setEndpointTemplate("http://127.0.0.1:2333");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{userId}");
    }

    @Test
    void enabledConfigurationRejectsUnboundedOutboxSettings() {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(true);
        properties.setEndpointTemplate("http://127.0.0.1/users/{userId}");
        properties.setOutboxBatchSize(201);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox-batch-size");
    }

    @Test
    void processingLeaseMustExceedRemoteCallTimeout() {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(true);
        properties.setEndpointTemplate("http://127.0.0.1/users/{userId}");
        properties.setTimeoutMs(3_000);
        properties.setProcessingLeaseSeconds(2);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("processing-lease-seconds");
    }
}
