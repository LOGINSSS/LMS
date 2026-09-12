package com.lms.learning.config;

import com.lms.common.constants.Constant;
import com.lms.common.utils.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 用户头透传（lms-learning → 下游业务模块）
 *
 * 与 lms-exam UserInfoFeignConfig 同模式：把当前线程 UserContext（网关 user-info 头写入）
 * 拼成 user-info 头转发给下游（lms-course 等），下游按 UserContext 归属处理。
 */
@Configuration
public class UserInfoFeignConfig {

    @Bean
    public RequestInterceptor userInfoFeignInterceptor() {
        return template -> {
            Long userId = UserContext.getUser();
            Integer userType = UserContext.getUserType();
            if (userId != null) {
                String userInfo = "{\"userId\":" + userId
                        + ",\"userType\":" + (userType == null ? 1 : userType) + "}";
                template.header(Constant.HEADER_USER_INFO, userInfo);
            }
        };
    }
}
