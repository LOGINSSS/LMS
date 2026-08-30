package com.lms.ai.tools;

import com.lms.ai.client.LearningClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 学习工具组（spec §3.3：learning-agent 工具面，Feign → lms-learning）
 */
@Component
@RequiredArgsConstructor
public class LearningTools {

    private final LearningClient client;

    @Tool(name = "lessonList", description = "课程课次列表", readOnly = true)
    public String lessonList(@ToolParam(name = "courseId", description = "课程 id") Long courseId) {
        try {
            return ToolSupport.json(ToolSupport.check(client.lessonList(courseId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "lessonDetail", description = "课次详情", readOnly = true)
    public String lessonDetail(@ToolParam(name = "id", description = "课次 id") Long id) {
        try {
            return ToolSupport.json(ToolSupport.check(client.lessonDetail(id)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "reportProgress", description = "上报学习进度（当前用户），progress 0-100")
    public String reportProgress(
            @ToolParam(name = "lessonId", description = "课次 id") Long lessonId,
            @ToolParam(name = "progress", description = "进度百分比 0-100，默认0", required = false) Integer progress,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("lessonId", lessonId);
            body.put("progress", progress);
            ToolSupport.check(client.reportProgress(body));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "courseProgress", description = "我的课程学习进度（0-100）", readOnly = true)
    public String courseProgress(@ToolParam(name = "courseId", description = "课程 id") Long courseId, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.courseProgress(courseId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "myStats", description = "我的学习统计（笔记/问答/签到/积分总数）", readOnly = true)
    public String myStats(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.myStats()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "notePage", description = "笔记分页（可按课程过滤）", readOnly = true)
    public String notePage(
            @ToolParam(name = "courseId", description = "课程 id", required = false) Long courseId,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认20", required = false) Integer pageSize) {
        try {
            return ToolSupport.json(ToolSupport.check(client.notePage(courseId, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "addNote", description = "新增笔记（当前用户）")
    public String addNote(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "lessonId", description = "课次 id（课程级笔记可空）", required = false) Long lessonId,
            @ToolParam(name = "content", description = "笔记内容") String content,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("courseId", courseId);
            body.put("lessonId", lessonId);
            body.put("content", content);
            return ToolSupport.json(ToolSupport.check(client.addNote(body)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "signIn", description = "签到（当前用户，每日一次，+5 积分）")
    public String signIn(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.signIn());
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "pointsRecords", description = "我的积分明细（分页）", readOnly = true)
    public String pointsRecords(
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认10", required = false) Integer pageSize,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.pointsRecords(pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "pointsBoard", description = "积分榜 Top N", readOnly = true)
    public String pointsBoard(@ToolParam(name = "size", description = "条数，默认10", required = false) Integer size) {
        try {
            return ToolSupport.json(ToolSupport.check(client.pointsBoard(size)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "qaPage", description = "问答分页（含回答）", readOnly = true)
    public String qaPage(
            @ToolParam(name = "courseId", description = "课程 id", required = false) Long courseId,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认20", required = false) Integer pageSize) {
        try {
            return ToolSupport.json(ToolSupport.check(client.qaPage(courseId, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "askQuestion", description = "发布提问（当前用户）")
    public String askQuestion(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "title", description = "问题标题") String title,
            @ToolParam(name = "content", description = "问题详情", required = false) String content,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("courseId", courseId);
            body.put("title", title);
            body.put("content", content);
            return ToolSupport.json(ToolSupport.check(client.askQuestion(body)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
