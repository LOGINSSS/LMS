package com.lms.ai.infrastructure.memory;

import com.lms.ai.config.ReMeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** HTTP adapter for the current ReMe Job service (POST /search, /write, /edit, /delete, /auto_memory). */
@Component
public class ReMeHttpJobClient implements ReMeJobClient {

    private final ReMeProperties properties;
    private final RestClient restClient;

    @Autowired
    public ReMeHttpJobClient(ReMeProperties properties) {
        this(properties, createBuilder(properties));
    }

    ReMeHttpJobClient(ReMeProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBearerToken());
        }
        this.restClient = builder.build();
    }

    private static RestClient.Builder createBuilder(ReMeProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public String search(Long userId, String query, int limit) {
        ReMeResponse response = post(userId, "search", Map.of("query", query, "limit", limit), ReMeResponse.class);
        return response == null ? null : response.answer();
    }

    @Override
    public void write(Long userId, String path, String name, String description, String content) {
        post(userId, "write", Map.of(
                "path", path,
                "name", name,
                "description", description,
                "content", content), Void.class);
    }

    @Override
    public void edit(Long userId, String path, String oldText, String newText) {
        post(userId, "edit", Map.of("path", path, "old", oldText, "new", newText), Void.class);
    }

    @Override
    public void delete(Long userId, String path) {
        post(userId, "delete", Map.of("path", path), Void.class);
    }

    @Override
    public void autoMemory(Long userId, String sessionId, List<SourceMessage> messages, String memoryHint) {
        post(userId, "auto_memory", Map.of(
                "session_id", sessionId,
                "messages", messages,
                "memory_hint", memoryHint), Void.class);
    }

    private <T> T post(Long userId, String job, Object body, Class<T> responseType) {
        return restClient.post()
                .uri(jobUri(userId, job))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    private URI jobUri(Long userId, String job) {
        String endpoint = properties.endpointFor(userId);
        return URI.create(endpoint + "/" + job);
    }

    private record ReMeResponse(String answer) {
    }
}
