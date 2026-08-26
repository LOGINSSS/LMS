package com.lms.gateway.filter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置属性
 *
 * 绑定 Nacos lms-gateway.yaml 的 lms.gateway.* 配置，供鉴权过滤器读取免登录白名单。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lms.gateway")
public class GatewayAuthProperties {

    /** 免登录白名单（ant 风格路径，如 /auth/login、/webjars/**） */
    private List<String> whitelist = new ArrayList<>();
}
