package com.lms.common.autoconfigure.mvc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 用户头拦截器注册
 *
 * 用途：按配置 lms.mvc.user-header-enabled=true 开启用户信息透传拦截器，
 * 默认关闭，未接入登录体系的服务（如 lms-ai）不受影响。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lms.mvc", name = "user-header-enabled", havingValue = "true")
public class UserInfoInterceptorConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
