package com.lms.course.course.client;

import com.lms.common.domain.R;
import com.lms.course.course.client.dto.UserSimpleDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * lms-user 服务降级工厂
 *
 * 业务规则：查询教师档案失败（服务不可用/超时）时返回 null（而非抛出异常），
 * 由建课逻辑兜底使用默认昵称「教师」，保证建课主流程不被跨服务故障拖垮。
 */
@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        log.warn("调用 lms-user 查询用户档案失败：{}", cause == null ? "未知原因" : cause.getMessage());
        return new UserClient() {
            @Override
            public R<UserSimpleDTO> queryUserById(Long id) {
                return R.ok(null);
            }
        };
    }
}
