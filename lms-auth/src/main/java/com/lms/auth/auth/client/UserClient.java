package com.lms.auth.auth.client;

import com.lms.auth.auth.client.dto.UserProfileDTO;
import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * lms-user 服务 Feign 客户端：跨服务创建用户档案
 *
 * 用途：注册账号时同步调用 lms-user 创建用户档案，是认证服务访问用户服务的唯一跨服务入口。
 *
 * 适用场景：register() 注册流程中，账号落库后调用本接口创建档案并回填 user_id。
 *
 * 注意点：
 * - 返回类型用 R 包装（与 lms-user 控制器返回结构一致），调用方需取 data 字段
 * - 创建失败由 UserClientFallbackFactory 降级抛异常，触发 register() 本地事务回滚（账号一并撤销）
 */
@FeignClient(name = "lms-user", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    /**
     * 创建用户档案
     *
     * @param dto 档案信息（accountId 关联登录账号 id，扩展字段按用户类型透传）
     * @return 统一响应体 R，data 为档案 id（用于回填 account.user_id）
     */
    @PostMapping("/users")
    R<Long> createUserProfile(@RequestBody UserProfileDTO dto);
}
