package com.lms.ai.tools;

import cn.hutool.json.JSONUtil;
import com.lms.ai.context.SessionLink;
import com.lms.ai.orchestration.LearningPipelineService;
import com.lms.ai.task.TaskService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 学习评测管道工具组（learning-agent 工具面，评测业务线）
 *
 * 前缀 pipeline：把「诊断→规划→习题→测评」固定管道暴露为 learning-agent 的工具，
 * student-agent 通过邀请 learning-agent（SubAgentTool）间接触发（对话语义判断由 LLM 决定何时调用）。
 * 工具参数避免复杂类型：答题结果用 JSON 数组字符串传入，内部解析。
 * P5 会话链接：工具调用时把 RuntimeContext 的数字会话号挂到线程（SessionLink），
 * 管道节点产物行据此写 session_id/action_type=pipeline（任务树可见）。
 */
@Component
@RequiredArgsConstructor
public class LearningPipelineTools {

    private final LearningPipelineService pipeline;

    @Tool(name = "evaluate", description = "学情评估全链一次：学情诊断→课程规划→习题推送（测评需学生答题后单独调用 assess）。学生说'帮我诊断/规划学习/推荐练习'时使用", readOnly = true)
    public String evaluate(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "count", description = "期望题目数量（可选）", required = false) Integer count,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            return link(ctx, () -> ToolSupport.json(pipeline.evaluate(userId, courseId, count)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "diagnose", description = "学情诊断（管道节点①）：读取学习数据中心，输出学情报告（薄弱知识点）", readOnly = true)
    public String diagnose(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            return link(ctx, () -> pipeline.diagnose(userId, courseId));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "plan", description = "课程规划（管道节点②）：基于学情报告生成个性化学习路径。report 缺省则先自动诊断", readOnly = true)
    public String plan(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "report", description = "学情诊断报告（可选，缺省自动诊断）", required = false) String report,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            return link(ctx, () -> {
                String r = report == null || report.isBlank() ? pipeline.diagnose(userId, courseId) : report;
                return pipeline.plan(userId, courseId, r);
            });
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "exercise", description = "AI 自测出题（管道节点③）：基于学习路径从课程知识库生成适配练习题（不出题库题，防透题）。path 缺省则自动诊断+规划", readOnly = true)
    public String exercise(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "path", description = "学习路径（可选，缺省自动诊断+规划）", required = false) String path,
            @ToolParam(name = "count", description = "期望题目数量（可选）", required = false) Integer count,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            return link(ctx, () -> {
                String p = path == null || path.isBlank()
                        ? pipeline.plan(userId, courseId, pipeline.diagnose(userId, courseId))
                        : path;
                return pipeline.exercise(userId, courseId, p, count);
            });
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "assess", description = "AI 自测批改（管道节点④）：基于本课程最近一次练习题目包参考答案判分学生作答（不触题库），输出评估报告并写回学习数据中心。学生交卷后使用")
    public String assess(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "answersJson", description = "答题结果 JSON 数组字符串，如 [{\"questionId\":123,\"userAnswer\":\"A\"}]") String answersJson,
            RuntimeContext ctx) {
        Long userId = ToolSupport.enter(ctx);
        try {
            cn.hutool.json.JSONArray arr = JSONUtil.parseArray(answersJson);
            List<Map<String, Object>> answers = new java.util.ArrayList<>();
            for (Object o : arr) {
                answers.add(new java.util.HashMap<>((cn.hutool.json.JSONObject) o));
            }
            return link(ctx, () -> pipeline.assess(userId, courseId, answers));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    /** 线程挂会话号执行（会话号来自 RuntimeContext，非数字（eval 管道）→ null 不落 session） */
    private String link(RuntimeContext ctx, java.util.function.Supplier<String> supplier) {
        Long session = TaskService.numericSession(ctx == null ? null : ctx.getSessionId());
        return SessionLink.with(session, supplier);
    }
}
