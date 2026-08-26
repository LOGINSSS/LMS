package com.lms.auth.auth.client;

import com.lms.auth.auth.client.dto.UserProfileDTO;
import com.lms.auth.auth.constants.AuthErrorInfo;
import com.lms.common.domain.R;
import com.lms.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * lms-user 服务 Feign 降级工厂
 *
 * 用途：调用 lms-user 创建用户档案失败（服务不可用 / 异常 / 超时）时提供降级实现。
 *
 * 降级语义：不返回兜底数据，而是抛出 REGISTER_FAILED 业务异常；
 * 该异常向上传播到 register()，使 @Transactional 本地事务回滚，注册的账号一并撤销，
 * 保证「账号 - 档案」两侧数据一致性。
 */
@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    /**
     * 创建降级代理：记录失败原因，返回一个总是抛 REGISTER_FAILED 的 UserClient 实现
     *
     * @param cause Feign 调用失败的原始异常（可能为 null）
     * @return 降级 UserClient 实现
     */
    @Override
    public UserClient create(Throwable cause) {
        log.error("调用 lms-user 创建用户档案失败：{}", cause == null ? "未知原因" : cause.getMessage(), cause);
        return new UserClient() {
            @Override
            public R<Long> createUserProfile(UserProfileDTO dto) {
                throw new CommonException(AuthErrorInfo.REGISTER_FAILED);
            }
        };
    }
}
