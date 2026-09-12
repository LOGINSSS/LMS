package com.lms.ai.infrastructure.memory;

import com.lms.ai.config.ReMeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ReMeHttpJobClientSpringContextTest {

    @Test
    void springSelectsTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ReMeProperties.class, ReMeProperties::new);
            context.register(ReMeHttpJobClient.class);

            context.refresh();

            assertThat(context.getBean(ReMeJobClient.class))
                    .isInstanceOf(ReMeHttpJobClient.class);
        }
    }
}
