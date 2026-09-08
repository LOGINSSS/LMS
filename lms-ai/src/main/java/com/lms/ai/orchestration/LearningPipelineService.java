package com.lms.ai.orchestration;

import com.lms.ai.task.AgentTask;
import com.lms.ai.task.AgentTaskMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习评测管道（AI 自测：诊断→规划→习题→测评，透题红线 §8.9，需求文档 §2/§5）
 *
 * 设计（与父 agent 自由邀请不同）：**固定管道 + 节点 Agent 增强**——
 * 节点顺序与数据依赖由代码确定（诊断输出→规划输入→习题→测评），
 * 节点内部是带工具的 ReActAgent（读学习数据中心/检索知识库）。
 * **出题**（节点③）：exercise-agent 仅从课程知识库（kb.ragChatCourse）检索素材生成题目，
 * 严禁触碰题库/exam 工具（防透题）；题目包（含参考答案）整包落 agent_task，学生端只拿去答案题目。
 * **判分**（节点④）：assess-agent 基于题目包自带参考答案语义批改（不再调 exam.getQuestion），
 * Java 只负责解析判分明细并直写学习数据中心（§6.1 数据可靠性）；
 * LLM 输出解析失败时用 QuestionChecker 确定性兜底（客观题按包内参考答案 JSON 比对）。
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

    /**
     * 节点③ 习题推送（AI 自测，透题红线 §8.9 R1/R3）：
     * 基于学习路径 → 仅从**课程知识库**检索素材生成练习题目（禁止触题库/exam 工具），
     * 题目包（含标准答案）整包落 agent_task（exercise_pack），对外只返回去答案的题目 JSON + packId。
     */
    public String exercise(Long userId, Long courseId, String planPath, Integer count) {
        String prompt = "学习路径如下：\n" + planPath
                + "\n\n请基于该学习路径中的当前知识点生成适配练习题目（AI 自测）：\n"
                + "1. 对需要出题的知识点调用 kb.ragChatCourse（courseId=" + courseId + "）检索课程知识库讲义/资料要点作为出题素材；\n"
                + "2. 仅基于知识库检索内容生成题目，题目须覆盖对应知识点；【禁止】使用题库/exam 任何工具，禁止检索考试题库（防止透题）；\n"
                + "3. 输出题目包 JSON：" + "\n{\"knowledgePoint\":\"知识点\",\"recommendation\":\"推送理由\",\"questions\":["
                + "{\"qid\":1,\"stem\":\"题干\",\"type\":1,\"options\":[\"选项A\",\"选项B\"],\"difficulty\":1,"
                + "\"category\":\"知识点\",\"answer\":\"{\\\"option\\\":\\\"A\\\"}\",\"analysis\":\"解析\"}]}\n"
                + "type：1单选 2多选 3判断（判断题 options 可空）；answer 格式：单选{\"option\":\"A\"} 多选{\"options\":[\"A\",\"B\"]} "
                + "判断{\"judge\":true}；qid 从 1 递增；题量"
                + (count == null ? "默认 8 题" : "约 " + count + " 题")
                + "；不得编造知识库内容，检索不到的知识点要减少题量并注明。";
        String raw = runNode("exercise-agent", "exercise_pack", userId, courseId, prompt);
        return toPublicPack(raw, userId, courseId);
    }

    /**
     * 节点④ 效果测评（AI 自测，透题红线 §8.9 R3）：
     * **LLM 语义批改**（assess-agent 基于题目包自带参考答案判分，不再触碰题库）→
     * Java 解析判分明细写回错题与测评报告（数据可靠性 §6.1）；LLM 输出解析失败时
     * 用 QuestionChecker 确定性兜底（客观题按题目包参考答案 JSON 比对，保证不丢判分）。
     *
     * @param userId  学生
     * @param courseId 课程
     * @param answers 答题结果列表：[{questionId(=题目包 qid), userAnswer, knowledgePoint?}]
     */
    public String assess(Long userId, Long courseId, List<Map<String, Object>> answers) {
        if (answers == null || answers.isEmpty()) {
            throw new CommonException("答题结果不能为空");
        }
        // 1. 取该学生最近一次练习题目包（exercise_pack，含参考答案，服务端留存不外泄）
        AgentTask pack = findLatestPack(userId, courseId);
        if (pack == null || pack.getResult() == null || pack.getResult().isBlank()) {
            throw new CommonException("未找到本次练习题目包，请先完成练习再交卷测评");
        }
        Map<Long, PackQuestion> packMap = parsePack(pack.getResult());
        if (packMap.isEmpty()) {
            throw new CommonException("练习题目包解析失败，请联系老师");
        }

        // 2. LLM 批改：把题干+参考答案（仅内部判分用）注入 assess-agent，输出带每题判分的 JSON 报告
        StringBuilder qb = new StringBuilder();
        for (PackQuestion q : packMap.values()) {
            qb.append("- qid=").append(q.qid()).append(" 题干：").append(q.stem())
                    .append(" type=").append(q.type());
            if (q.options() != null && !q.options().isEmpty()) {
                qb.append(" options=").append(q.options());
            }
            qb.append(" 参考答案：").append(q.answer())
                    .append(" 解析：").append(q.analysis() == null ? "" : q.analysis()).append("\n");
        }
        String prompt = "请批改以下学生作答并输出 JSON 报告。每题的题干与参考答案如下"
                + "（参考答案仅用于判分，严禁出现在你的输出中向学生泄露）：\n课程 id：" + courseId
                + "\n题目包（含参考答案）：\n" + qb
                + "\n作答列表：" + ToolSupport.json(answers)
                + "\n\n必须输出 JSON（不要输出其他内容）："
                + "{\"gradedQuestions\":[{\"questionId\":123,\"correct\":1,\"score\":1,\"knowledgePoint\":\"分类\"}],"
                + "\"totalScore\":85,\"mastery\":{\"知识点A\":90},\"weakPoints\":[\"...\"],\"summary\":\"评估总结与建议\"}";
        String report = runNode("assess-agent", "assess_report", userId, courseId, prompt);

        // 3. Java 解析判分明细（失败/缺失 → QuestionChecker 按题目包参考答案确定性兜底）
        List<Map<String, Object>> graded = extractGraded(report, answers, packMap);

        // 4. 写回做题记录（Java 直写，保证数据可靠性）
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

        // 5. 写回测评报告（闭环回流）
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
     * （客观题按**题目包参考答案** JSON 严格比对，不触题库）。
     */
    private List<Map<String, Object>> extractGraded(String report, List<Map<String, Object>> answers,
                                                    Map<Long, PackQuestion> packMap) {
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

        // 兜底：逐题按题目包参考答案确定性比对（无包内题目 → 记为 0 分并告警）
        for (Map<String, Object> answer : answers) {
            Long questionId = Long.valueOf(String.valueOf(answer.get("questionId")));
            String userAnswer = answer.get("userAnswer") == null ? "" : String.valueOf(answer.get("userAnswer"));
            PackQuestion q = packMap.get(questionId);
            if (q == null) {
                log.warn("题目包无此 qid={}（判 0 分）", questionId);
                Map<String, Object> m = new HashMap<>();
                m.put("questionId", questionId);
                m.put("knowledgePoint", answer.get("knowledgePoint"));
                m.put("userAnswer", userAnswer);
                m.put("correct", 0);
                m.put("score", 0);
                graded.add(m);
                continue;
            }
            try {
                boolean correct = questionChecker.check(q.type(), q.answer(), userAnswer);
                Map<String, Object> m = new HashMap<>();
                m.put("questionId", questionId);
                m.put("questionType", q.type());
                m.put("knowledgePoint", q.category());
                m.put("userAnswer", userAnswer);
                m.put("correct", correct ? 1 : 0);
                m.put("score", correct ? 1 : 0);
                graded.add(m);
            } catch (Exception e) {
                log.warn("题目判分兜底失败 qid={}: {}", questionId, e.getMessage());
                Map<String, Object> m = new HashMap<>();
                m.put("questionId", questionId);
                m.put("knowledgePoint", q.category());
                m.put("userAnswer", userAnswer);
                m.put("correct", 0);
                m.put("score", 0);
                graded.add(m);
            }
        }
        return graded;
    }

    // ---------- 题目包（AI 自测，KB 生成，含参考答案，服务端留存） ----------

    /** 题目包单题（qid 为包内序号，1 递增；answer 为 JSON 字符串，格式同题库答案） */
    public record PackQuestion(Long qid, String stem, Integer type, List<String> options,
                               String answer, String analysis, String category) {
    }

    /** 解析题目包 JSON → qid → PackQuestion */
    private Map<Long, PackQuestion> parsePack(String packJson) {
        Map<Long, PackQuestion> map = new LinkedHashMap<>();
        if (packJson == null || packJson.isBlank()) {
            return map;
        }
        try {
            cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(packJson);
            cn.hutool.json.JSONArray arr = obj.getJSONArray("questions");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    cn.hutool.json.JSONObject q = arr.getJSONObject(i);
                    List<String> options = new ArrayList<>();
                    cn.hutool.json.JSONArray opts = q.getJSONArray("options");
                    if (opts != null) {
                        for (Object o : opts) {
                            options.add(String.valueOf(o));
                        }
                    }
                    PackQuestion pq = new PackQuestion(q.getLong("qid"),
                            q.getStr("stem"),
                            q.getInt("type", 1),
                            options.isEmpty() ? List.of() : List.copyOf(options),
                            q.getStr("answer"),
                            q.getStr("analysis"),
                            q.getStr("category"));
                    map.put(pq.qid(), pq);
                }
            }
        } catch (Exception e) {
            log.warn("题目包解析失败: {}", e.getMessage());
        }
        return map;
    }

    /** 对外返回：去答案/解析的题目 JSON + packId（题目包 id，交卷时定位用） */
    private String toPublicPack(String rawPack, Long userId, Long courseId) {
        try {
            AgentTask row = findLatestPack(userId, courseId);
            cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(rawPack);
            cn.hutool.json.JSONArray questions = obj.getJSONArray("questions");
            if (questions != null) {
                for (Object o : questions) {
                    cn.hutool.json.JSONObject q = (cn.hutool.json.JSONObject) o;
                    q.remove("answer");
                    q.remove("analysis");
                }
            }
            if (row != null) {
                obj.set("packId", row.getId());
            }
            return obj.toString();
        } catch (Exception e) {
            log.warn("题目包对外序列化失败，返回原文: {}", e.getMessage());
            return rawPack;
        }
    }

    /** 取该用户最近一次指定课程的练习题目包（exercise_pack 任务行，含参考答案） */
    private AgentTask findLatestPack(Long userId, Long courseId) {
        List<AgentTask> rows = taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getOwnerId, userId)
                .eq(AgentTask::getTaskType, "exercise_pack")
                .orderByDesc(AgentTask::getId)
                .last("LIMIT 10"));
        for (AgentTask t : rows) {
            if (t.getPayload() != null && t.getPayload().contains("\"courseId\":" + courseId)) {
                return t;
            }
        }
        return null;
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
            // P5 会话链接：管道工具经 SessionLink 挂的数字会话 → 任务树可见；action_type=pipeline
            task.setSessionId(com.lms.ai.context.SessionLink.current());
            task.setActionType(AgentTask.ACTION_PIPELINE);
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
