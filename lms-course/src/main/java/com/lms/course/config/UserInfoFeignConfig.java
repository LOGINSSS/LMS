package com.lms.course.config;

import com.lms.common.constants.Constant;
import com.lms.common.utils.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 用户头透传（lms-course → 业务模块，选课资格判定数据源）
 *
 * 与 lms-ai/lms-exam 同模式：本服务线程 UserContext（网关 user-info 头 → UserInfoInterceptor 写入）
 * 拼成 user-info 头转发给下游（lms-user / lms-learning 解析进其 UserContext），
 * 使资格判定读取的是"当前学生本人"的档案/进度/积分。
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
