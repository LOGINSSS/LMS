package com.lms.grab.config;

import com.lms.common.constants.Constant;
import com.lms.common.utils.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 用户头透传（lms-grab → lms-course 资格校验）
 *
 * 与 lms-ai/lms-exam/lms-course 同模式：本服务线程 UserContext（网关 user-info 头 → UserInfoInterceptor 写入）
 * 拼成 user-info 头转发给 lms-course，使资格判定读取"抢课学生本人"的档案/进度/积分。
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
