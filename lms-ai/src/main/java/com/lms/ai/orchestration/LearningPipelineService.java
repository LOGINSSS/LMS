package com.lms.ai.orchestration;

import com.lms.ai.task.AgentTask;
import com.lms.ai.task.AgentTaskMapper;
import com.lms.ai.client.ExamClient;
import com.lms.ai.client.LearningEvalClient;
import com.lms.ai.service.QuestionChecker;
import com.lms.ai.task.AgentTask;
import com.lms.ai.task.AgentTaskMapper;
import com.lms.ai.tools.ToolFactory;
import com.lms.ai.tools.ToolSupport;
import com.lms.common.exceptions.CommonException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习评测管道（评测业务线：诊断→规划→习题→测评，需求文档 §2/§5）
 *
 * 设计（与父 agent 自由邀请不同）：**固定管道 + 节点 Agent 增强**——
 * 节点顺序与数据依赖由代码确定（诊断输出→规划输入→习题→测评），
 * 节点内部是带工具的 ReActAgent（读学习数据中心/检索题库）。
 * **判分**（节点④）：主判分由 LLM（assess-agent 调 exam.getQuestion 语义批改），
 * Java 只负责解析判分明细并直写学习数据中心（§6.1 数据可靠性）；
 * LLM 输出解析失败时用 QuestionChecker 确定性兜底（客观题按标准答案 JSON 比对）。
 * 每个节点中间产物落 agent_task 留存（§6.4 可观测性）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPipelineService {

    /** @Lazy 打破循环依赖：pipeline → ToolFactory → LearningPipelineTools → pipeline */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private ToolFactory toolFactory;

    private final AgentTaskMapper taskMapper;
    private final ExamClient examClient;
    private final LearningEvalClient evalClient;
    private final QuestionChecker questionChecker;

    /** 节点① 学情诊断：读学习数据中心 → 输出诊断报告 */
    public String diagnose(Long userId, Long courseId) {
        String prompt = "请对当前学生做学情诊断（课程 " + courseId + "）。调用学情工具读取真实数据后输出诊断报告 JSON。";
        return runNode("diagnose-agent", "diagnose_report", userId, courseId, prompt);
    }

    /** 节点② 课程规划：诊断报告 → 个性化学习路径 */
    public String plan(Long userId, Long courseId, String diagnosisReport) {
        String prompt = "学情诊断报告如下：\n" + diagnosisReport
                + "\n\n请基于该诊断报告与课程知识库，生成该学生的个性化学习路径（JSON），覆盖薄弱知识点并标注重难点。";
        return runNode("plan-agent", "plan_path", userId, courseId, prompt);
    }

    /** 节点③ 习题推送：学习路径 → 适配题目清单 */
    public String exercise(Long userId, Long courseId, String planPath, Integer count) {
        String prompt = "学习路径如下：\n" + planPath
                + "\n\n请基于该学习路径与当前学习知识点，从题库检索并挑选适配题目"
                + (count == null ? "" : "（目标 " + count + " 题）")
                + "，输出题目清单 JSON（必须来自题库真实检索，禁止编造题目 id）。";
        return runNode("exercise-agent", "exercise_pack", userId, courseId, prompt);
    }

    /**
     * 节点④ 效果测评：**LLM 语义批改**（assess-agent 调 exam.getQuestion 取标准答案判分）→
     * Java 解析判分明细写回错题与测评报告（数据可靠性 §6.1）；LLM 输出解析失败时
     * 用 QuestionChecker 确定性兜底（客观题按标准答案 JSON 比对，保证不丢判分）。
     *
     * @param answers 答题结果列表：[{questionId, userAnswer, knowledgePoint?}]
     */
    public String assess(Long userId, Long courseId, List<Map<String, Object>> answers) {
        if (answers == null || answers.isEmpty()) {
            throw new CommonException("答题结果不能为空");
        }
        // 1. LLM 批改：assess-agent 拿标准答案语义判分，输出带每题判分的 JSON 报告
        String prompt = "请批改以下学生作答并输出 JSON 报告。每题调用 exam.getQuestion 获取标准答案与解析进行批改，"
                + "判分以你的批改结果为准。\n课程 id：" + courseId
                + "\n作答列表：" + ToolSupport.json(answers)
                + "\n\n必须输出 JSON（不要输出其他内容）："
                + "{\"gradedQuestions\":[{\"questionId\":123,\"correct\":1,\"score\":1,\"knowledgePoint\":\"分类\"}],"
                + "\"totalScore\":85,\"mastery\":{\"知识点A\":90},\"weakPoints\":[\"...\"],\"summary\":\"评估总结与建议\"}";
        String report = runNode("assess-agent", "assess_report", userId, courseId, prompt);

        // 2. Java 解析判分明细（失败/缺失 → QuestionChecker 确定性兜底）
        List<Map<String, Object>> graded = extractGraded(report, answers);

        // 3. 写回做题记录（Java 直写，保证数据可靠性）
        int totalScore = 0;
        for (Map<String, Object> g : graded) {
            int score = g.get("score") == null ? 0 : (Integer) g.get("score");
            totalScore += score;
            Map<String, Object> record = new HashMap<>();
            record.put("courseId", courseId);
            record.put("questionId", g.get("questionId"));
            record.put("questionType", g.get("questionType"));
            record.put("knowledgePoint", g.get("knowledgePoint"));
            record.put("userAnswer", g.get("userAnswer"));
            record.put("correct", g.get("correct"));
            record.put("score", score);
            record.put("source", 2); // 测评来源
            ToolSupport.check(evalClient.recordExercise(record));
        }

        // 4. 写回测评报告（闭环回流）
        Map<String, Object> assessment = new HashMap<>();
        assessment.put("courseId", courseId);
        assessment.put("title", "课程" + courseId + "单元测评");
        assessment.put("report", report);
        assessment.put("totalScore", totalScore);
        ToolSupport.check(evalClient.saveAssessment(assessment));
        return report;
    }

    /**
     * 从 LLM 输出中提取每题判分明细；解析失败或字段缺失时，逐题用 QuestionChecker 确定性兜底
     * （客观题按题库标准答案 JSON 严格比对）。
     */
    private List<Map<String, Object>> extractGraded(String report, List<Map<String, Object>> answers) {
        List<Map<String, Object>> graded = new ArrayList<>();
        cn.hutool.json.JSONArray arr = null;
        try {
            String json = extractJson(report);
            if (json != null) {
                cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(json);
                if (obj.containsKey("gradedQuestions")) {
                    arr = obj.getJSONArray("gradedQuestions");
                }
            }
        } catch (Exception e) {
            log.warn("评测 LLM 输出解析失败，走确定性兜底: {}", e.getMessage());
        }

        if (arr != null && !arr.isEmpty()) {
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject g = arr.getJSONObject(i);
                Map<String, Object> m = new HashMap<>();
                m.put("questionId", g.getLong("questionId"));
                m.put("correct", g.getInt("correct", 0));
                m.put("score", g.getInt("score", 0));
                m.put("knowledgePoint", g.getStr("knowledgePoint"));
                m.put("questionType", g.getInt("questionType"));
                m.put("userAnswer", g.getStr("userAnswer"));
                graded.add(m);
            }
            return graded;
        }

        // 兜底：逐题确定性比对（客观题按标准答案 JSON）
        for (Map<String, Object> answer : answers) {
            Long questionId = Long.valueOf(String.valueOf(answer.get("questionId")));
            String userAnswer = answer.get("userAnswer") == null ? "" : String.valueOf(answer.get("userAnswer"));
            try {
                Map<?, ?> q = (Map<?, ?>) ToolSupport.check(examClient.getQuestion(questionId));
                Integer type = q.get("type") == null ? null : Integer.valueOf(String.valueOf(q.get("type")));
                String answerJson = q.get("answer") == null ? null : String.valueOf(q.get("answer"));
                String kp = q.get("category") == null ? null : String.valueOf(q.get("category"));
                boolean correct = questionChecker.check(type, answerJson, userAnswer);
                Map<String, Object> m = new HashMap<>();
                m.put("questionId", questionId);
                m.put("questionType", type);
                m.put("knowledgePoint", kp);
                m.put("userAnswer", userAnswer);
                m.put("correct", correct ? 1 : 0);
                m.put("score", correct ? 1 : 0);
                graded.add(m);
            } catch (Exception e) {
                log.warn("题目判分兜底失败 questionId={}: {}", questionId, e.getMessage());
                Map<String, Object> m = new HashMap<>();
                m.put("questionId", questionId);
                m.put("knowledgePoint", answer.get("knowledgePoint"));
                m.put("userAnswer", answer.get("userAnswer"));
                m.put("correct", 0);
                m.put("score", 0);
                graded.add(m);
            }
        }
        return graded;
    }

    /** 从 LLM 输出里截取首个 { ... } JSON 片段（兼容 markdown 代码块包裹） */
    private String extractJson(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    /** 全链一次：诊断→规划→习题（测评需学生答题，单独触发） */
    public EvaluateResult evaluate(Long userId, Long courseId, Integer count) {
        String diagnosis = diagnose(userId, courseId);
        String plan = plan(userId, courseId, diagnosis);
        String exercises = exercise(userId, courseId, plan, count);
        return new EvaluateResult(diagnosis, plan, exercises, null);
    }

    /** 全链产物（中间结果留存并返回，需求文档 §6.4） */
    public record EvaluateResult(String diagnosis, String plan, String exercises, String assessment) {
    }

    // ---------- 节点执行 ----------

    private String runNode(String agentName, String taskType, Long userId, Long courseId, String prompt) {
        ReActAgent agent = toolFactory.provideSubAgent(agentName);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("eval-" + userId)
                .userId(String.valueOf(userId))
                .put(ToolSupport.CTX_USER_ID, userId)
                .put(ToolSupport.CTX_USER_TYPE, 1)
                .build();
        try {
            Msg result = agent.call(List.<Msg>of(new UserMessage(prompt)), ctx).block();
            String output = result == null ? "（节点无输出）" : result.getTextContent();
            // 中间产物留存（agent_task.result，§6.4 可观测性）
            persist(taskType, userId, courseId, output);
            log.info("评测管道节点完成 agent={} userId={} outputLen={}", agentName, userId, output.length());
            return output;
        } catch (Exception e) {
            log.error("评测管道节点失败 agent={} userId={}", agentName, userId, e);
            throw new CommonException("评测节点 " + agentName + " 执行失败: " + e.getMessage());
        }
    }

    /** 中间产物留存：诊断报告/学习路径/题目清单/评估报告 → agent_task（status=2 完成） */
    private void persist(String taskType, Long userId, Long courseId, String result) {
        try {
            AgentTask task = new AgentTask();
            task.setTaskId("eval-" + taskType + "-" + userId + "-" + System.currentTimeMillis());
            task.setOwnerId(userId);
            task.setAgentName(taskType);
            task.setTaskType(taskType);
            task.setPayload("{\"courseId\":" + courseId + "}");
            task.setTriggerType(AgentTask.TRIGGER_ONCE);
            task.setStatus(AgentTask.STATUS_DONE);
            task.setResult(result);
            task.setExecuteTime(java.time.LocalDateTime.now());
            task.setFinishTime(java.time.LocalDateTime.now());
            taskMapper.insert(task);
        } catch (Exception e) {
            log.warn("评测中间产物留存失败 taskType={}: {}", taskType, e.getMessage());
        }
    }
}
