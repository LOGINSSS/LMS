package com.lms.ai.client;

import com.lms.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * lms-user 用户服务 Feign 契约（spec §3.3：user-agent 工具面）
 *
 * UserProfileFormDTO：nickname/avatar/phone/email/college/title/bio/studentNo/major/grade/className（均可选，非 null 字段更新）。
 */
@FeignClient(name = "lms-user", contextId = "userClient")
public interface UserClient {

    @GetMapping("/users/me")
    R<Object> getMe();

    @PutMapping("/users/me")
    R<Void> updateMe(@RequestBody Map<String, Object> body);

    @GetMapping("/users/{id}")
    R<Object> getById(@PathVariable("id") Long id);
}
