package com.lms.ai.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客观题答案比对器（评测业务线节点④）
 *
 * 题库答案（lms-exam Question.answer）为结构化 JSON：
 * - 单选 {"option":"A"}；多选 {"options":["A","B"]}；判断 {"judge":true}
 * 判分必须确定性比对（不依赖 LLM 语义判断，需求文档 §4.5 批改打分可靠）：
 * - 单选：选项严格相等
 * - 多选：选项集合相等（顺序无关）
 * - 判断：布尔值等价（兼容 true/false、1/0、对/错 输入）
 */
@Component
public class QuestionChecker {

    /** 题型常量（与 lms-exam QuestionType 一致） */
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_MULTIPLE = 2;
    public static final int TYPE_JUDGE = 3;

    /**
     * 批改一道题
     *
     * @param questionType 题型：1单选 2多选 3判断
     * @param answerJson   题库标准答案 JSON（{"option":"A"} 等）
     * @param userAnswer   学生作答（兼容 JSON 或简写："A" / "A,B" / "true" / "对"）
     * @return 是否答对
     */
    public boolean check(Integer questionType, String answerJson, String userAnswer) {
        if (questionType == null || answerJson == null || answerJson.isBlank()
                || userAnswer == null || userAnswer.isBlank()) {
            return false;
        }
        return switch (questionType) {
            case TYPE_SINGLE -> checkSingle(answerJson, userAnswer);
            case TYPE_MULTIPLE -> checkMultiple(answerJson, userAnswer);
            case TYPE_JUDGE -> checkJudge(answerJson, userAnswer);
            default -> false;
        };
    }

    /** 单选：{"option":"A"} vs "A" 或 {"option":"A"} */
    private boolean checkSingle(String answerJson, String userAnswer) {
        String option = parseOption(answerJson);
        return option != null && option.equalsIgnoreCase(normalizeOption(userAnswer));
    }

    /** 多选：{"options":["A","B"]} vs "A,B" 或 {"options":[...]}（集合相等，顺序无关） */
    private boolean checkMultiple(String answerJson, String userAnswer) {
        Set<String> correct = parseOptions(answerJson);
        Set<String> user = parseOptions(userAnswer);
        return !correct.isEmpty() && correct.equals(user);
    }

    /** 判断：{"judge":true} vs true/false/1/0/对/错 */
    private boolean checkJudge(String answerJson, String userAnswer) {
        Boolean judge = parseJudge(answerJson);
        if (judge == null) {
            return false;
        }
        Boolean user = parseJudgeValue(userAnswer);
        return user != null && judge.equals(user);
    }

    // ---------- 解析 ----------

    private String parseOption(String json) {
        try {
            if (JSONUtil.isTypeJSON(json)) {
                JSONObject obj = JSONUtil.parseObj(json);
                Object o = obj.get("option");
                return o == null ? null : normalizeOption(String.valueOf(o));
            }
        } catch (Exception ignored) {
        }
        return normalizeOption(json);
    }

    private Set<String> parseOptions(String json) {
        try {
            if (JSONUtil.isTypeJSON(json)) {
                JSONObject obj = JSONUtil.parseObj(json);
                if (obj.containsKey("options")) {
                    return obj.getJSONArray("options").stream()
                            .map(String::valueOf)
                            .map(this::normalizeOption)
                            .collect(Collectors.toSet());
                }
                if (obj.containsKey("option")) {
                    return Set.of(normalizeOption(String.valueOf(obj.get("option"))));
                }
            }
        } catch (Exception ignored) {
        }
        // 简写："A,B" / "A B" / "AB"
        return Arrays.stream(json.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizeOption)
                .collect(Collectors.toSet());
    }

    private Boolean parseJudge(String json) {
        try {
            if (JSONUtil.isTypeJSON(json)) {
                JSONObject obj = JSONUtil.parseObj(json);
                Object j = obj.get("judge");
                if (j != null) {
                    return j instanceof Boolean b ? b : Boolean.valueOf(String.valueOf(j));
                }
            }
        } catch (Exception ignored) {
        }
        return parseJudgeValue(json);
    }

    private Boolean parseJudgeValue(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1", "对", "正确", "yes", "t" -> Boolean.TRUE;
            case "false", "0", "错", "错误", "no", "f" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** 选项归一：去空格、大写（"a " → "A"） */
    private String normalizeOption(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
