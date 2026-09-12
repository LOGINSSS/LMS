package com.lms.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Current ReMe HTTP Job service configuration. Each resolved endpoint must map to one
 * user-owned ReMe workspace; current ReMe search does not accept a workspace parameter.
 */
@Data
@ConfigurationProperties(prefix = "lms.ai.agent.reme")
public class ReMeProperties {

    /** 是否启用 ReMe 个人 Wiki；未启用时召回仍使用 MySQL 画像。 */
    private boolean enabled = false;

    /**
     * 按用户路由的可信服务模板，必须包含 {userId}。例如反向代理地址：
     * http://reme-gateway.internal/users/{userId}。代理必须将每个用户路由到独立 workspace。
     */
    private String endpointTemplate = "";

    /** 请求超时（毫秒，默认 3s——避免记忆检索拖慢对话，官方默认 60s 偏长） */
    private long timeoutMs = 3000;

    /** 可选：由可信反向代理校验的 Bearer token；不要写入仓库配置。 */
    private String bearerToken = "";

    /** Outbox 每轮最多处理的任务数。 */
    private int outboxBatchSize = 20;

    /** 单个任务最大投递次数，达到后进入死信状态。 */
    private int maxAttempts = 5;

    /** 首次重试间隔；后续按 2 的指数退避。 */
    private long retryBaseSeconds = 30;

    /** PROCESSING 租约，实例崩溃后到期任务可被其他实例接管。 */
    private long processingLeaseSeconds = 120;

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (endpointTemplate == null || !endpointTemplate.contains("{userId}")) {
            throw new IllegalStateException("启用 ReMe 时 endpoint-template 必须包含 {userId}，以隔离个人 workspace");
        }
        String probe = endpointTemplate.replace("{userId}", "1");
        URI uri = URI.create(probe);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("ReMe endpoint-template 仅支持 http/https");
        }
        if (timeoutMs <= 0) {
            throw new IllegalStateException("ReMe timeout-ms 必须为正数");
        }
        if (outboxBatchSize <= 0 || outboxBatchSize > 200) {
            throw new IllegalStateException("ReMe outbox-batch-size 必须在 1~200 之间");
        }
        if (maxAttempts <= 0 || maxAttempts > 20 || retryBaseSeconds <= 0 || processingLeaseSeconds <= 0) {
            throw new IllegalStateException("ReMe Outbox 重试与租约配置必须为正数且 max-attempts 不超过 20");
        }
        long minimumLeaseSeconds = Math.floorDiv(timeoutMs, 1000) + 1;
        if (processingLeaseSeconds < minimumLeaseSeconds) {
            throw new IllegalStateException("ReMe processing-lease-seconds 必须大于远端请求 timeout-ms");
        }
    }

    public String endpointFor(Long userId) {
        validate();
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正数");
        }
        String endpoint = endpointTemplate.replace("{userId}", userId.toString());
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }
}
