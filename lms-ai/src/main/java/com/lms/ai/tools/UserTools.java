package com.lms.ai.tools;

import com.lms.ai.client.UserClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户工具组（spec §3.3：user-agent 工具面，Feign → lms-user）
 */
@Component
@RequiredArgsConstructor
public class UserTools {

    private final UserClient client;

    @Tool(name = "getMe", description = "我的用户详情", readOnly = true)
    public String getMe(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.getMe()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "updateMe", description = "修改我的资料（只更新传入字段）")
    public String updateMe(
            @ToolParam(name = "nickname", description = "昵称", required = false) String nickname,
            @ToolParam(name = "avatar", description = "头像 URL", required = false) String avatar,
            @ToolParam(name = "phone", description = "手机号", required = false) String phone,
            @ToolParam(name = "email", description = "邮箱", required = false) String email,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("nickname", nickname);
            body.put("avatar", avatar);
            body.put("phone", phone);
            body.put("email", email);
            ToolSupport.check(client.updateMe(body));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
