package com.lms.ai.tools;

import com.lms.ai.client.ExamClient;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 考试题库工具组（spec §3.3：exam-agent 工具面，Feign → lms-exam）
 *
 * 建题/改题/删题/绑定需老师身份；answer 为 JSON 字符串：
 * 单选 {"option":"A"} / 多选 {"options":["A","B"]} / 判断 {"judge":true}。
 */
@Component
@RequiredArgsConstructor
public class ExamTools {

    private final ExamClient client;

    @Tool(name = "saveQuestion", description = "新建题目（老师），返回题目 id。type: 1单选 2多选 3判断；difficulty: 1易 2中 3难；answer 为 JSON 字符串")
    public String saveQuestion(
            @ToolParam(name = "name", description = "题目名称/题干") String name,
            @ToolParam(name = "type", description = "题型：1单选 2多选 3判断") Integer type,
            @ToolParam(name = "category", description = "分类（如：数据结构）", required = false) String category,
            @ToolParam(name = "difficulty", description = "难度：1易 2中 3难") Integer difficulty,
            @ToolParam(name = "analysis", description = "解析", required = false) String analysis,
            @ToolParam(name = "answer", description = "答案 JSON 字符串：单选{\"option\":\"A\"} 多选{\"options\":[\"A\",\"B\"]} 判断{\"judge\":true}", required = false) String answer,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            body.put("type", type);
            body.put("category", category);
            body.put("difficulty", difficulty);
            body.put("analysis", analysis);
            body.put("answer", answer);
            return ToolSupport.json(ToolSupport.check(client.saveQuestion(body)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "updateQuestion", description = "修改题目（老师），只更新非 null 字段")
    public String updateQuestion(
            @ToolParam(name = "id", description = "题目 id") Long id,
            @ToolParam(name = "name", description = "题目名称/题干", required = false) String name,
            @ToolParam(name = "type", description = "题型：1单选 2多选 3判断", required = false) Integer type,
            @ToolParam(name = "category", description = "分类", required = false) String category,
            @ToolParam(name = "difficulty", description = "难度：1易 2中 3难", required = false) Integer difficulty,
            @ToolParam(name = "analysis", description = "解析", required = false) String analysis,
            @ToolParam(name = "answer", description = "答案 JSON 字符串", required = false) String answer,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            body.put("type", type);
            body.put("category", category);
            body.put("difficulty", difficulty);
            body.put("analysis", analysis);
            body.put("answer", answer);
            ToolSupport.check(client.updateQuestion(id, body));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "deleteQuestion", description = "删除题目（老师）")
    public String deleteQuestion(@ToolParam(name = "id", description = "题目 id") Long id, RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.deleteQuestion(id));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }

    @Tool(name = "queryQuestionPage", description = "题目分页查询（可按题型/分类/难度过滤）", readOnly = true)
    public String queryQuestionPage(
            @ToolParam(name = "type", description = "题型：1单选 2多选 3判断", required = false) Integer type,
            @ToolParam(name = "category", description = "分类", required = false) String category,
            @ToolParam(name = "difficulty", description = "难度：1易 2中 3难", required = false) Integer difficulty,
            @ToolParam(name = "pageNo", description = "页码，默认1", required = false) Integer pageNo,
            @ToolParam(name = "pageSize", description = "每页条数，默认20", required = false) Integer pageSize) {
        try {
            return ToolSupport.json(ToolSupport.check(
                    client.queryQuestionPage(type, category, difficulty, pageNo, pageSize)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "queryBizQuestions", description = "按业务查询已绑定题目（bizType: 1课程 2章节 3考试卷）", readOnly = true)
    public String queryBizQuestions(
            @ToolParam(name = "bizType", description = "业务类型：1课程 2章节 3考试卷") Integer bizType,
            @ToolParam(name = "bizId", description = "业务 id（课程/章节/考试卷 id）") Long bizId) {
        try {
            return ToolSupport.json(ToolSupport.check(client.queryBizQuestions(bizType, bizId)));
        } catch (Exception e) {
            return ToolSupport.fail(e);
        }
    }

    @Tool(name = "bindToBiz", description = "把题目绑定到业务（bizType: 1课程 2章节 3考试卷，老师），score 分值默认0")
    public String bindToBiz(
            @ToolParam(name = "id", description = "题目 id") Long id,
            @ToolParam(name = "bizType", description = "业务类型：1课程 2章节 3考试卷") Integer bizType,
            @ToolParam(name = "bizId", description = "业务 id（课程/章节/考试卷 id）") Long bizId,
            @ToolParam(name = "score", description = "分值，默认0", required = false) Integer score,
            RuntimeContext ctx) {
        ToolSupport.enter(ctx);
        try {
            ToolSupport.check(client.bindToBiz(id, bizType, bizId, score));
            return "ok";
        } catch (Exception e) {
            return ToolSupport.fail(e);
        } finally {
            ToolSupport.exit();
        }
    }
}
