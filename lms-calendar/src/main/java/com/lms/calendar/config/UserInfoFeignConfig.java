package com.lms.calendar.config;

import com.lms.common.constants.Constant;
import com.lms.common.utils.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 用户头透传（lms-calendar → lms-course / lms-exam）
 *
 * 与其余模块同模式：本服务线程 UserContext（网关 user-info 头）拼 user-info 头转发，
 * 使各域查询读取"当前用户本人"的课表/考试。
 */
@Configuration
public class UserInfoFeignConfig {

    @Bean
    public RequestInterceptor userInfoFeignInterceptor() {
        return template -> {
            Long userId = UserContext.getUser();
            if (userId != null) {
                Integer userType = UserContext.getUserType();
                String userInfo = "{\"userId\":" + userId
                        + ",\"userType\":" + (userType == null ? 1 : userType) + "}";
                template.header(Constant.HEADER_USER_INFO, userInfo);
            }
        };
    }
}
