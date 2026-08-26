package com.lms.course.course.client;

import com.lms.common.domain.R;
import com.lms.course.course.client.dto.UserSimpleDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * lms-user 服务客户端：查询用户档案简要信息
 *
 * 用途：教师创建课程时，按当前教师 id 查询其昵称，冗余写入 course.teacher_name，
 * 卡片展示无需每次跨服务联查。
 * 注意：返回类型用 R 包装（与 lms-user 控制器响应结构一致），调用方取 data；
 * 查询失败由 {@link UserClientFallbackFactory} 降级返回 null，业务层兜底默认昵称。
 */
@FeignClient(name = "lms-user", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    /**
     * 按档案 id 查询用户简要信息
     *
     * @param id 用户档案 id（lms_user.user.id）
     * @return 统一响应体，data 为用户简要信息；查询失败时降级为 null
     */
    @GetMapping("/users/{id}")
    R<UserSimpleDTO> queryUserById(@PathVariable("id") Long id);
}
