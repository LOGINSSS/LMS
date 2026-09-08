package com.lms.exam.config;

import com.lms.common.constants.Constant;
import com.lms.common.utils.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 用户头透传（lms-exam → 业务模块）
 *
 * 与 lms-ai AgentConfig 同模式：本服务线程内 UserContext（网关 user-info 头 → UserInfoInterceptor 写入）
 * 拼成 user-info 头转发给下游（lms-learning UserInfoInterceptor 解析进其 UserContext），
 * 使"作业/考试作答回流"以作答学生身份写入学习数据中心。
 */
@Configuration
public class UserInfoFeignConfig {

    @Bean
    public RequestInterceptor userInfoFeignInterceptor() {
        return template -> {
            Long userId = UserContext.getUser();
            Integer userType = UserContext.getUserType();
            if (userId == null) {
                // 异步消费线程无 HTTP 上下文 → 回退 AsyncUser（考试 Kafka 提交端设置）
                userId = AsyncUser.getUserId();
                userType = AsyncUser.getUserType();
            }
            if (userId != null) {
                String userInfo = "{\"userId\":" + userId
                        + ",\"userType\":" + (userType == null ? 1 : userType) + "}";
                template.header(Constant.HEADER_USER_INFO, userInfo);
            }
        };
    }
}
