package com.lms.ai.config;

import com.lms.ai.context.AgentContextHolder;
import com.lms.common.constants.Constant;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 运行时基础设施配置
 *
 * - Feign 用户头透传：工具方法把 RuntimeContext 用户身份桥到 AgentContextHolder，
 *   这里统一拼 user-info 头转发给业务模块（业务模块 UserInfoInterceptor 解析进 UserContext）。
 */
@Configuration
public class AgentConfig {

    /** Feign 调用业务模块时补 user-info 头（spec §2.2：网关透传约定的复用） */
    @Bean
    public RequestInterceptor userInfoFeignInterceptor() {
        return template -> {
            Long userId = AgentContextHolder.getUserId();
            if (userId != null) {
                Integer userType = AgentContextHolder.getUserType();
                String userInfo = "{\"userId\":" + userId
                        + ",\"userType\":" + (userType == null ? 1 : userType) + "}";
                template.header(Constant.HEADER_USER_INFO, userInfo);
            }
        };
    }
}
