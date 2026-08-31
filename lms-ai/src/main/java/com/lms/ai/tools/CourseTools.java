package com.lms.ai.tools;

import com.lms.ai.client.CourseClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 课程工具组（spec §3.3：course-agent 工具面，Feign → lms-course）
 *
 * 高副作用操作（建课/上下架）需老师身份（业务模块校验），读操作公开。
 */
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient client;

    @Tool(name = "addCourse", description = "创建课程（老师），返回课程 id。参数：name 课程名(必填), cover 封面URL, intro 简介, category 分类")
    public String addCourse(
            @ToolParam(name = "name", description = "课程名（必填）") String name,
            @ToolParam(name = "cover", description = "封面 URL", required = false) String cover,
            @ToolParam(name = "intro", description = "简介", required = false) String intro,
            @ToolParam(name = "category", description = "分类", required = false) String category,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            body.put("cover", cover);
            body.put("intro", intro);
            body.put("category", category);
            return ToolSupport.json(ToolSupport.check(client.addCourse(body)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "updateCourse", description = "修改课程（老师本人），只更新非 null 字段")
    public String updateCourse(
            @ToolParam(name = "id", description = "课程 id") Long id,
            @ToolParam(name = "name", description = "课程名", required = false) String name,
            @ToolParam(name = "cover", description = "封面 URL", required = false) String cover,
            @ToolParam(name = "intro", description = "简介", required = false) String intro,
            @ToolParam(name = "category", description = "分类", required = false) String category,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            body.put("cover", cover);
            body.put("intro", intro);
            body.put("category", category);
            ToolSupport.check(client.updateCourse(id, body));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "changeStatus", description = "上下架课程（老师本人）。status: 0 下架 / 1 上架")
    public String changeStatus(
            @ToolParam(name = "id", description = "课程 id") Long id,
            @ToolParam(name = "status", description = "状态：0 下架 / 1 上架") Integer status,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.changeStatus(id, status));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "myCourses", description = "我的课程（老师），分页返回课程卡片列表", readOnly = true)
    public String myCourses(
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认20", required = false) Integer pageSize,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.myCourses(pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "getDetail", description = "课程详情（含选课人数）", readOnly = true)
    public String getDetail(@ToolParam(name = "id", description = "课程 id") Long id) {
        try {
            return ToolSupport.json(ToolSupport.check(client.getDetail(id)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "pageCourses", description = "课程分页（已发布），keyword 模糊匹配名称/简介", readOnly = true)
    public String pageCourses(
            @ToolParam(name = "keyword", description = "关键词", required = false) String keyword,
            @ToolParam(name = "category", description = "分类", required = false) String category,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认20", required = false) Integer pageSize) {
        try {
            return ToolSupport.json(ToolSupport.check(client.pageCourses(keyword, category, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "enroll", description = "选课（学生）")
    public String enroll(@ToolParam(name = "id", description = "课程 id") Long id, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.enroll(id));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "quit", description = "退课（学生）")
    public String quit(@ToolParam(name = "id", description = "课程 id") Long id, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.quit(id));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    // ---------- 0.2 课程内容域工具（目录/章节/发布） ----------

    @Tool(name = "catalog", description = "课程目录树（左栏大纲，章/节两级）", readOnly = true)
    public String catalog(@ToolParam(name = "courseId", description = "课程 id") Long courseId) {
        try {
            return ToolSupport.json(ToolSupport.check(client.catalog(courseId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "chapter", description = "章节正文（markdown，右栏内容）", readOnly = true)
    public String chapter(@ToolParam(name = "catalogId", description = "章节目录节点 id") Long catalogId) {
        try {
            return ToolSupport.json(ToolSupport.check(client.chapter(catalogId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "publishCourse", description = "提交课程发布（老师，设置抢课窗口与名额），grabStartTime/grabEndTime 为 ISO 时间")
    public String publishCourse(
            @ToolParam(name = "id", description = "课程 id") Long id,
            @ToolParam(name = "grabStartTime", description = "抢课开始时间（如 2026-03-01T10:00:00）") String grabStartTime,
            @ToolParam(name = "grabEndTime", description = "抢课结束时间（如 2026-03-01T12:00:00）") String grabEndTime,
            @ToolParam(name = "stock", description = "抢课名额，0=不限", required = false) Integer stock,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.publish(id, grabStartTime, grabEndTime, stock == null ? 0 : stock));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
