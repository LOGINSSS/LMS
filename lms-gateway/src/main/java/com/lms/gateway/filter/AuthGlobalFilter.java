package com.lms.gateway.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * 网关统一鉴权过滤器
 *
 * 职责：对进入网关的请求做统一登录校验——白名单路径（登录/注册/文档等）直接放行；
 * 其余请求校验 Authorization 头中的 JWT（签名 + 有效期），并查询 Redis 登出黑名单，
 * 全部通过后把 userId/userType 写入 user-info 头透传给下游业务服务。
 *
 * 配置来源：JWT 密钥与 Redis 连接来自 Nacos 共享配置 lms-common.yaml；
 * 白名单来自 Nacos lms-gateway.yaml 的 lms.gateway.whitelist。
 * 注意：本过滤器为 GlobalFilter，仅对命中路由的请求生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayAuthProperties authProperties;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${lms.jwt.secret}")
    private String jwtSecret;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        //1. 白名单放行：登录/注册/接口文档等免鉴权路径直接通过
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        //2. 取 token：从 Authorization 头解析 Bearer token，缺失或格式错误直接拒绝
        String authorization = request.getHeaders().getFirst(GatewayConstants.HEADER_AUTHORIZATION);
        if (StrUtil.isBlank(authorization) || !authorization.startsWith(GatewayConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange);
        }
        String token = authorization.substring(GatewayConstants.TOKEN_PREFIX.length());

        //3. 校验 JWT：验签 + 有效期，防止伪造与过期 token 进入业务服务
        Map<String, Object> claims;
        try {
            JWT jwt = JWT.of(token).setKey(jwtSecret.getBytes(StandardCharsets.UTF_8));
            JWTValidator.of(jwt).validateAlgorithm().validateDate(new Date());
            claims = jwt.getPayloads();
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return unauthorized(exchange);
        }

        //4. 查登出黑名单：命中说明该 token 已主动登出，即使未过期也拒绝（靠 jti + Redis TTL 兜底）
        String jti = StrUtil.toString(claims.get(GatewayConstants.CLAIM_JTI));
        return redisTemplate.opsForValue()
                .get(GatewayConstants.JWT_BLACKLIST_KEY + jti)
                .flatMap(blacklisted -> unauthorized(exchange))
                .switchIfEmpty(Mono.defer(() -> {
                    //5. 透传用户信息：把 userId/userType 写入 user-info 头，业务服务据此识别当前用户
                    String userInfo = "{\"userId\":" + claims.get(GatewayConstants.CLAIM_USER_ID)
                            + ",\"userType\":" + claims.get(GatewayConstants.CLAIM_USER_TYPE) + "}";
                    ServerHttpRequest mutated = request.mutate()
                            .header(GatewayConstants.HEADER_USER_INFO, userInfo)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                }));
    }

    private boolean isWhitelisted(String path) {
        return authProperties.getWhitelist().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(GatewayConstants.UNAUTHORIZED_BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
