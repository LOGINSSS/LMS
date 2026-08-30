package com.lms.ai.tools;

import com.lms.ai.client.LearningEvalClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 评测数据工具组（评测业务线：诊断/习题/测评节点 agent 的工具面）
 *
 * 前缀 eval：诊断 agent 读学情聚合、习题 agent 查做题记录、测评 agent 写回错题与测评报告
 * （需求文档 §4.2/§4.4/§4.5/§4.7：学习数据中心回流闭环）。
 */
@Component
@RequiredArgsConstructor
public class EvalTools {

    private final LearningEvalClient client;

    @Tool(name = "getDiagnosis", description = "学情聚合（诊断智能体输入）：总做题数/正确率/各知识点掌握度/薄弱知识点/活跃天数", readOnly = true)
    public String getDiagnosis(RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.diagnosis()));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "myExercises", description = "我的做题记录分页（可按课程/错题过滤），诊断与习题节点查看历史作答", readOnly = true)
    public String myExercises(
            @ToolParam(name = "courseId", description = "课程 id（可选）", required = false) Long courseId,
            @ToolParam(name = "onlyWrong", description = "只看错题（true/false）", required = false) Boolean onlyWrong,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认10", required = false) Integer pageSize,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.myExercises(courseId, onlyWrong, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "submitExercise", description = "上报做题记录到学习数据中心（测评节点写回流：错题集原料）。courseId 必填，correct 1对0错，knowledgePoint 知识点")
    public String submitExercise(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "questionId", description = "题目 id（0 表示测评临时题）", required = false) Long questionId,
            @ToolParam(name = "questionType", description = "题型：1单选 2多选 3判断", required = false) Integer questionType,
            @ToolParam(name = "knowledgePoint", description = "对应知识点", required = false) String knowledgePoint,
            @ToolParam(name = "userAnswer", description = "学生作答", required = false) String userAnswer,
            @ToolParam(name = "correct", description = "是否答对：1对 0错") Integer correct,
            @ToolParam(name = "score", description = "本题得分，默认0", required = false) Integer score,
            @ToolParam(name = "source", description = "来源：1练习 2测评，默认1", required = false) Integer source,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("courseId", courseId);
            body.put("questionId", questionId);
            body.put("questionType", questionType);
            body.put("knowledgePoint", knowledgePoint);
            body.put("userAnswer", userAnswer);
            body.put("correct", correct);
            body.put("score", score);
            body.put("source", source);
            return ToolSupport.json(ToolSupport.check(client.recordExercise(body)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "submitAssessment", description = "上报测评结果到学习数据中心（闭环回流）：courseId 必填，report 评估报告文本，knowledgeMastery 为知识点掌握度 JSON 字符串")
    public String submitAssessment(
            @ToolParam(name = "courseId", description = "课程 id") Long courseId,
            @ToolParam(name = "title", description = "测评标题", required = false) String title,
            @ToolParam(name = "report", description = "评估报告（智能体输出文本）") String report,
            @ToolParam(name = "totalScore", description = "总分", required = false) Integer totalScore,
            @ToolParam(name = "knowledgeMastery", description = "知识点掌握度 JSON 字符串，如 {\"图论\":80,\"排序\":45}", required = false) String knowledgeMastery,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("courseId", courseId);
            body.put("title", title);
            body.put("report", report);
            body.put("totalScore", totalScore);
            body.put("knowledgeMastery", knowledgeMastery);
            return ToolSupport.json(ToolSupport.check(client.saveAssessment(body)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "myAssessments", description = "我的测评报告列表（可按课程过滤），诊断节点参考历史测评", readOnly = true)
    public String myAssessments(
            @ToolParam(name = "courseId", description = "课程 id（可选）", required = false) Long courseId,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            return ToolSupport.json(ToolSupport.check(client.myAssessments(courseId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
