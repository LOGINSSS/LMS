package com.lms.ai.tools;

import com.lms.ai.client.RemarkClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 点赞工具组（spec §3.3：remark-agent 工具面，Feign → lms-remark）
 *
 * bizType：1 课程 / 2 笔记 / 3 问答。
 */
@Component
@RequiredArgsConstructor
public class RemarkTools {

    private final RemarkClient client;

    @Tool(name = "toggleLike", description = "点赞/取消点赞（切换，幂等）。bizType: 1课程 2笔记 3问答")
    public String toggleLike(
            @ToolParam(name = "bizType", description = "业务类型：1课程 2笔记 3问答") Integer bizType,
            @ToolParam(name = "bizId", description = "业务 id") Long bizId,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.toggleLike(bizType, bizId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "likeCount", description = "点赞数查询", readOnly = true)
    public String likeCount(
            @ToolParam(name = "bizType", description = "业务类型：1课程 2笔记 3问答") Integer bizType,
            @ToolParam(name = "bizId", description = "业务 id") Long bizId) {
        try {
            return ToolSupport.json(ToolSupport.check(client.likeCount(bizType, bizId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "likeStatuses", description = "批量点赞状态（bizIds 逗号分隔，返回已赞 id→true）", readOnly = true)
    public String likeStatuses(
            @ToolParam(name = "bizType", description = "业务类型：1课程 2笔记 3问答") Integer bizType,
            @ToolParam(name = "bizIds", description = "业务 id 列表，逗号分隔") String bizIds,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.likeStatuses(bizType, bizIds)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
