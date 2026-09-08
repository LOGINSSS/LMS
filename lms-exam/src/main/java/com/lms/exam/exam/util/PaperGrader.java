package com.lms.exam.exam.util;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 试卷确定性判分器（v1 收尾：服务端按卷面答案快照判分，不依赖 LLM，客观题严格比对）
 *
 * 答案 JSON 格式与题库一致：单选 {"option":"A"} / 多选 {"options":["A","B"]} / 判断 {"judge":true}。
 * 学生作答宽容归一化：单选取字母（忽略大小写/前后缀），多选支持 ,、，空格分隔，判断支持 true/false/对/错/√/×/1/0。
 */
public final class PaperGrader {

    private static final Set<String> TRUE_WORDS = Set.of("true", "t", "1", "yes", "y", "正确", "对", "√");
    private static final Set<String> FALSE_WORDS = Set.of("false", "f", "0", "no", "n", "错误", "错", "×");

    private PaperGrader() {
    }

    public static boolean check(Integer type, String answerJson, String userAnswer) {
        if (type == null || StrUtil.isBlank(answerJson) || StrUtil.isBlank(userAnswer)) {
            return false;
        }
        return switch (type) {
            case 1 -> checkSingle(answerJson, userAnswer);   // 单选
            case 2 -> checkMulti(answerJson, userAnswer);    // 多选
            case 3 -> checkJudge(answerJson, userAnswer);    // 判断
            default -> false;
        };
    }

    private static boolean checkSingle(String answerJson, String userAnswer) {
        String expect = rawValue(answerJson, "option");
        if (expect == null) {
            return false;
        }
        return normLetter(expect).equals(normLetter(userAnswer));
    }

    private static boolean checkMulti(String answerJson, String userAnswer) {
        String[] items = arrayItems(answerJson, "options");
        if (items == null || items.length == 0) {
            return false;
        }
        Set<String> expect = new HashSet<>();
        for (String it : items) {
            expect.add(normLetter(it));
        }
        Set<String> got = new HashSet<>();
        for (String part : userAnswer.split("[,，、\\s]+")) {
            if (!part.isBlank()) {
                got.add(normLetter(part));
            }
        }
        return expect.equals(got);
    }

    private static boolean checkJudge(String answerJson, String userAnswer) {
        String expect = rawValue(answerJson, "judge");
        if (expect == null) {
            return false;
        }
        Boolean e = parseBool(expect);
        Boolean g = parseBool(userAnswer);
        return e != null && e.equals(g);
    }

    /** 归一化单选字母：去空白/前后缀取首个字母，转大写 */
    private static String normLetter(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().toUpperCase();
        // 支持 "A." / "A、A)" 等：取第一个 A-Z 字符
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return String.valueOf(c);
            }
        }
        return t;
    }

    private static Boolean parseBool(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toLowerCase();
        if (TRUE_WORDS.contains(t)) {
            return Boolean.TRUE;
        }
        if (FALSE_WORDS.contains(t)) {
            return Boolean.FALSE;
        }
        return null;
    }

    // ---------- 极简 JSON 取值（依赖极小的 {"k":v} 形态，避免引入解析依赖） ----------

    /** 取顶层字符串/布尔/数字键值（"key": "value" 或 "key": value） */
    private static String rawValue(String json, String key) {
        int i = json.indexOf('"' + key + '"');
        if (i < 0) {
            return null;
        }
        int c = json.indexOf(':', i);
        if (c < 0) {
            return null;
        }
        int p = c + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        if (p >= json.length()) {
            return null;
        }
        if (json.charAt(p) == '"') {
            int end = json.indexOf('"', p + 1);
            return end < 0 ? null : json.substring(p + 1, end);
        }
        int end = json.length();
        for (int k = p; k < json.length(); k++) {
            char ch = json.charAt(k);
            if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                end = k;
                break;
            }
        }
        return json.substring(p, end);
    }

    /** 取数组键的元素（"key": ["A","B"]），返回去掉引号的元素列表 */
    private static String[] arrayItems(String json, String key) {
        int i = json.indexOf('"' + key + '"');
        if (i < 0) {
            return null;
        }
        int c = json.indexOf('[', i);
        if (c < 0) {
            return null;
        }
        int end = json.indexOf(']', c);
        if (end < 0) {
            return null;
        }
        String body = json.substring(c + 1, end);
        List<String> out = new ArrayList<>();
        for (String part : body.split(",")) {
            String t = part.trim();
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
                t = t.substring(1, t.length() - 1);
            }
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out.toArray(new String[0]);
    }
}
