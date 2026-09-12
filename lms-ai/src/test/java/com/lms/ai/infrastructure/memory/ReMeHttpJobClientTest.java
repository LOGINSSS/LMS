package com.lms.ai.infrastructure.memory;

import com.lms.ai.config.ReMeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReMeHttpJobClientTest {

    @Test
    void usesPerUserCurrentJobEndpointsAndAllowlistedBodies() {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(true);
        properties.setEndpointTemplate("http://reme.internal/users/{userId}");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReMeHttpJobClient client = new ReMeHttpJobClient(properties, builder);

        server.expect(once(), requestTo("http://reme.internal/users/7/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"query\":\"学习计划\",\"limit\":5}"))
                .andRespond(withSuccess("{\"answer\":\"Wiki result\",\"metadata\":{}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://reme.internal/users/7/write"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"path\":\"digest/wiki/id.md\",\"name\":\"Preference\",\"description\":\"Learning preference\",\"content\":\"body\"}"))
                .andRespond(withSuccess());
        server.expect(once(), requestTo("http://reme.internal/users/7/auto_memory"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"session_id\":\"lms-agent-99\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\",\"created_at\":\"2026-09-11T12:00:00Z\"}],\"memory_hint\":\"learning only\"}"))
                .andRespond(withSuccess());

        assertThat(client.search(7L, "学习计划", 5)).isEqualTo("Wiki result");
        client.write(7L, "digest/wiki/id.md", "Preference", "Learning preference", "body");
        client.autoMemory(7L, "lms-agent-99", List.of(
                new ReMeJobClient.SourceMessage("user", "hello", "2026-09-11T12:00:00Z")), "learning only");
        server.verify();
    }

    @Test
    void sendsConfiguredProxyBearerToken() {
        ReMeProperties properties = new ReMeProperties();
        properties.setEnabled(true);
        properties.setEndpointTemplate("https://reme.internal/users/{userId}");
        properties.setBearerToken("secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReMeHttpJobClient client = new ReMeHttpJobClient(properties, builder);
        server.expect(requestTo("https://reme.internal/users/7/delete"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andRespond(withSuccess());

        client.delete(7L, "digest/wiki/id.md");
        server.verify();
    }
}
