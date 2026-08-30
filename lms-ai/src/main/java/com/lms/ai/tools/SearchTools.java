package com.lms.ai.tools;

import com.lms.ai.client.SearchClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 搜索工具组（spec §3.3：search-agent 工具面，Feign → lms-search）
 */
@Component
@RequiredArgsConstructor
public class SearchTools {

    private final SearchClient client;

    @Tool(name = "courses", description = "课程搜索（keyword 模糊匹配名称/简介，可按分类过滤）", readOnly = true)
    public String courses(
            @ToolParam(name = "keyword", description = "关键词", required = false) String keyword,
            @ToolParam(name = "category", description = "分类", required = false) String category,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认10", required = false) Integer pageSize) {
        try {
            return ToolSupport.json(ToolSupport.check(client.courses(keyword, category, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "recommend", description = "兴趣推荐课程（基于用户兴趣标签）", readOnly = true)
    public String recommend(@ToolParam(name = "size", description = "条数，默认10", required = false) Integer size, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.recommend(size)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "recordInterest", description = "上报兴趣标签（当前用户，用于推荐与画像）")
    public String recordInterest(@ToolParam(name = "tag", description = "兴趣标签（≤50 字）") String tag, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("tag", tag);
            ToolSupport.check(client.recordInterest(body));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "myInterests", description = "我的兴趣标签列表（按权重降序）", readOnly = true)
    public String myInterests(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.myInterests()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
