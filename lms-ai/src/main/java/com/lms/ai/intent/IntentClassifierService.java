package com.lms.ai.intent;

import com.lms.ai.config.HarnessV2Properties;
import com.lms.ai.llm.ModelCaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图 LLM 分类层（spec HEAVY_HARNESS_SPEC §4.2 ②：规则层未命中时的二级识别）
 *
 * - 模型：qwen-turbo（summary-model 分级，成本低），temperature=0，输出受 JSON 约束；
 * - 候选集按角色收窄（学生/老师），减少分类空间与误路由；
 * - 输出解析失败 / 置信低于阈值 → 返回 empty，上层走"自由 ReAct"兜底（只记 Trace，不影响可用性）；
 * - 开关：lms.ai.harness-v2.intent-llm-enabled（默认 false：规则层足够时不开，避免每轮额外调用成本）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifierService {

    private static final Pattern CONF_PATTERN = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)");

    private final ModelCaller modelCaller;
    private final HarnessV2Properties v2;

    @Value("${lms.ai.agent.summary-model:qwen-turbo}")
    private String classifierModel;

    /** 学生侧候选意图 */
    private static final List<Intent> STUDENT_INTENTS = List.of(
            Intent.DIRECT_CHAT, Intent.QUERY_LEARNING, Intent.NOTE_SIGN_QA,
            Intent.DIAGNOSE_PLAN, Intent.EXERCISE_ASSESS, Intent.ASK_TEACHER,
            Intent.KB_RAG, Intent.MEDIA_FILE);

    /** 老师侧候选意图 */
    private static final List<Intent> TEACHER_INTENTS = List.of(
            Intent.DIRECT_CHAT, Intent.GENERATE_EXAM, Intent.GENERATE_COURSE,
            Intent.TEACHER_IM, Intent.TASK_MANAGE, Intent.KB_RAG, Intent.QUERY_LEARNING);

    /**
     * LLM 分类（仅规则层未命中时调用）
     *
     * @return 命中且置信达标 → decision(source=llm)；否则 empty
     */
    public Optional<IntentDecision> classify(Integer userType, String agentType, String text) {
        if (!v2.isIntentLlmEnabled() || text == null
                || text.length() > v2.getIntentLlmMaxTextLen()) {
            return Optional.empty();
        }
        boolean teacher = userType != null && userType == 2
                || "teacher-agent".equals(agentType);
        List<Intent> candidates = teacher ? TEACHER_INTENTS : STUDENT_INTENTS;
        StringBuilder sb = new StringBuilder();
        for (Intent it : candidates) {
            sb.append("- ").append(it.name()).append("：").append(meaning(it)).append("\n");
        }
        String system = "你是 LMS 平台的学习意图分类器。只从下列候选意图中选择最匹配的一个，"
                + "并输出 JSON（不要输出其他内容）：{\"intent\":\"意图枚举名\",\"confidence\":0.0-1.0}\n候选意图：\n" + sb;
        String user = "用户消息：" + text;
        String raw = modelCaller.chat(classifierModel, system, user, 128);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Intent intent = pick(raw, candidates);
        if (intent == null) {
            log.debug("意图分类输出无法解析: {}", raw);
            return Optional.empty();
        }
        double conf = parseConfidence(raw);
        if (conf < v2.getIntentMinConfidence()) {
            return Optional.empty();
        }
        return Optional.of(new IntentDecision(intent, conf, "llm", null));
    }

    private Intent pick(String raw, List<Intent> candidates) {
        int best = Integer.MAX_VALUE;
        Intent hit = null;
        for (Intent it : candidates) {
            int idx = raw.indexOf(it.name());
            if (idx >= 0 && idx < best) {
                best = idx;
                hit = it;
            }
        }
        return hit;
    }

    private double parseConfidence(String raw) {
        Matcher m = CONF_PATTERN.matcher(raw);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.6; // 无显式置信 → 保守按阈值处理（低于 min 会被上层丢弃）
    }

    private static String meaning(Intent it) {
        return switch (it) {
            case DIRECT_CHAT -> "闲聊/一般问答，无需工具";
            case QUERY_LEARNING -> "查询学习状态/进度/统计";
            case NOTE_SIGN_QA -> "笔记/签到/积分/提问";
            case DIAGNOSE_PLAN -> "学情诊断/学习规划";
            case EXERCISE_ASSESS -> "练习/自测/测评（知识库出题）";
            case GENERATE_EXAM -> "生成考试/出题/试卷（老师）";
            case GENERATE_COURSE -> "生成课程/大纲/章节（老师）";
            case ASK_TEACHER -> "向老师提问";
            case TEACHER_IM -> "推送消息给老师小助手";
            case KB_RAG -> "知识库问答";
            case TASK_MANAGE -> "定时任务管理";
            case MEDIA_FILE -> "上传/删除文件";
            default -> "其他";
        };
    }
}
