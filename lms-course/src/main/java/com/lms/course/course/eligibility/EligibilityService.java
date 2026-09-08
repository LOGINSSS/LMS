package com.lms.course.course.eligibility;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lms.common.exceptions.CommonException;
import com.lms.common.utils.AssertUtils;
import com.lms.course.course.client.LearningClient;
import com.lms.course.course.client.UserDetailClient;
import com.lms.course.course.domain.dto.EnrollRuleForm;
import com.lms.course.course.domain.po.CourseEnrollRule;
import com.lms.course.course.domain.po.CourseEnrollment;
import com.lms.course.course.domain.vo.EligibilityVO;
import com.lms.course.course.enums.EnrollmentStatus;
import com.lms.course.course.mapper.CourseEnrollRuleMapper;
import com.lms.course.course.mapper.CourseEnrollmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 选课资格判定引擎（课程广场权限校验核心）
 *
 * 约束不是查询条件（无法一条 SQL 直查），而是**声明式规则 + 逐条处理器**：
 * - 规则随课程存 course_enroll_rule.rule_json：{"mode":"ALL|ANY","rules":[...]}；
 * - check(userId, courseId) 逐条解析，按 type 分派数据源（本地选课记录 / lms-user 档案 /
 *   lms-learning 积分与学习进度，Feign 实时拉取），多查询拼接天然在此收敛；
 * - 校验时点：课程广场提示 / enroll 强校验 / grab(抢课) Redis 预检前强校验，防绕过；
 * - 支持类型：grade(年级) major(专业) college(学院) points_min(积分下限)
 *   enrolled(已选课程，在读即可) course_done(已修完课程，进度100%)；未知类型保存时即拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EligibilityService {

    /** 支持的类型（扩展：在此登记 + evaluate 增加分支 + 前端提示文案） */
    public static final List<String> SUPPORTED_TYPES =
            List.of("grade", "major", "college", "points_min", "enrolled", "course_done");

    private static final String MODE_ALL = "ALL";
    private static final String MODE_ANY = "ANY";

    private final CourseEnrollRuleMapper ruleMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final UserDetailClient userDetailClient;
    private final LearningClient learningClient;

    // ==================== 保存 / 读取 ====================

    /** 保存课程规则（rules 为空 = 不限选，等价删除）；先校验类型与组合模式 */
    public void save(Long courseId, EnrollRuleForm form) {
        AssertUtils.notNull(courseId, "课程 id 不能为空");
        String mode = form == null || StrUtil.isBlank(form.getMode()) ? MODE_ALL : form.getMode().trim().toUpperCase();
        AssertUtils.isTrue(MODE_ALL.equals(mode) || MODE_ANY.equals(mode), "组合模式仅支持 ALL / ANY");
        List<EnrollRuleForm.RuleItem> rules = form == null ? List.of() : form.getRules();
        JSONArray arr = new JSONArray();
        if (rules != null) {
            for (EnrollRuleForm.RuleItem it : rules) {
                if (it.getType() == null) {
                    continue;
                }
                String type = it.getType().trim();
                AssertUtils.isTrue(SUPPORTED_TYPES.contains(type), "不支持的规则类型: " + type);
                JSONObject o = new JSONObject();
                o.set("type", type);
                o.set("op", StrUtil.blankToDefault(it.getOp(), "IN"));
                o.set("values", it.getValues() == null ? new JSONArray() : JSONUtil.parseArray(it.getValues()));
                if (it.getCourseId() != null) {
                    o.set("courseId", it.getCourseId());
                }
                if (it.getValue() != null) {
                    o.set("value", it.getValue());
                }
                arr.add(o);
            }
        }
        JSONObject root = new JSONObject();
        root.set("mode", mode);
        root.set("rules", arr);
        String ruleJson = root.toString();

        CourseEnrollRule existing = ruleMapper.selectOne(new LambdaQueryWrapper<CourseEnrollRule>()
                .eq(CourseEnrollRule::getCourseId, courseId));
        if (existing == null) {
            CourseEnrollRule row = new CourseEnrollRule();
            row.setCourseId(courseId);
            row.setRuleJson(ruleJson);
            ruleMapper.insert(row);
        } else {
            existing.setRuleJson(ruleJson);
            ruleMapper.updateById(existing);
        }
    }

    /** 读取课程规则 JSON 原文（管理端展示/微调用；无规则返回 null） */
    public String getRuleJson(Long courseId) {
        CourseEnrollRule row = ruleMapper.selectOne(new LambdaQueryWrapper<CourseEnrollRule>()
                .eq(CourseEnrollRule::getCourseId, courseId));
        return row == null ? null : row.getRuleJson();
    }

    /** 删除课程规则（不限选） */
    public void remove(Long courseId) {
        ruleMapper.delete(new LambdaQueryWrapper<CourseEnrollRule>()
                .eq(CourseEnrollRule::getCourseId, courseId));
    }

    // ==================== 判定 ====================

    /**
     * 资格判定（enroll / grab 预检 / 课程广场提示共用）
     *
     * @return allowed=true 可报名；false 时 reasons 为未满足说明（可直接展示）
     */
    public EligibilityVO check(Long userId, Long courseId) {
        EligibilityVO vo = new EligibilityVO();
        CourseEnrollRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<CourseEnrollRule>()
                .eq(CourseEnrollRule::getCourseId, courseId));
        if (rule == null || StrUtil.isBlank(rule.getRuleJson())) {
            vo.setAllowed(true); // 无规则 = 不限选
            return vo;
        }
        List<Item> items;
        String mode;
        try {
            JSONObject root = JSONUtil.parseObj(rule.getRuleJson());
            mode = StrUtil.blankToDefault(root.getStr("mode"), MODE_ALL);
            items = parseItems(root.getJSONArray("rules"));
        } catch (Exception e) {
            log.warn("选课规则解析失败（按放行并提示）courseId={}: {}", courseId, e.getMessage());
            vo.setAllowed(true);
            vo.setReasons(List.of("本课程选课规则配置异常，请联系老师"));
            return vo;
        }
        vo.setMode(mode);
        vo.setTotal(items.size());
        if (items.isEmpty()) {
            vo.setAllowed(true);
            return vo;
        }

        List<String> unmet = new ArrayList<>();
        int matched = 0;
        for (Item it : items) {
            Result r = evaluate(userId, it);
            if (r.pass()) {
                matched++;
            } else {
                unmet.add(r.reason());
            }
        }
        vo.setMatched(matched);
        if (MODE_ANY.equalsIgnoreCase(mode)) {
            vo.setAllowed(matched > 0);
            if (!vo.isAllowed()) {
                vo.getReasons().add("本课程要求满足任一条件，当前均不满足：");
                vo.getReasons().addAll(unmet);
            }
        } else {
            vo.setAllowed(unmet.isEmpty());
            vo.getReasons().addAll(unmet);
        }
        return vo;
    }

    // ==================== 逐条判定 ====================

    private record Item(String type, String op, List<String> values, Long courseId, Integer value) {
    }

    private record Result(boolean pass, String reason) {
    }

    private List<Item> parseItems(JSONArray arr) {
        List<Item> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (Object o : arr) {
            JSONObject j = (JSONObject) o;
            List<String> vals = new ArrayList<>();
            JSONArray va = j.getJSONArray("values");
            if (va != null) {
                for (Object v : va) {
                    vals.add(String.valueOf(v));
                }
            }
            out.add(new Item(j.getStr("type"), StrUtil.blankToDefault(j.getStr("op"), "IN"),
                    vals, j.getLong("courseId"), j.getInt("value")));
        }
        return out;
    }

    private Result evaluate(Long userId, Item it) {
        if (userId == null) {
            return new Result(false, "未登录，无法校验选课资格");
        }
        return switch (it.type()) {
            case "grade", "major", "college" -> matchProfile(userId, it);
            case "points_min" -> checkPoints(userId, it);
            case "enrolled" -> checkEnrolled(userId, it, false);
            case "course_done" -> checkEnrolled(userId, it, true);
            default -> new Result(false, "未知规则类型：" + it.type());
        };
    }

    /** 档案字段（grade 年级 / major 专业 / college 学院）与期望值比较（IN/EQ/NE） */
    private Result matchProfile(Long userId, Item it) {
        String actual = profileField(userId, it.type());
        String label = switch (it.type()) {
            case "grade" -> "年级";
            case "major" -> "专业";
            default -> "学院";
        };
        String expect = String.join("/", it.values());
        boolean pass = compareField(actual, it.op(), it.values());
        return new Result(pass,
                "需" + label + "为 " + expect + (pass ? "" : "（当前：" + (actual.isBlank() ? "未填写" : actual) + "）"));
    }

    /** 拉取档案字段；档案获取失败返回空串（按未满足处理，reason 自带说明） */
    private String profileField(Long userId, String field) {
        try {
            var r = userDetailClient.queryUserById(userId);
            if (r == null || !r.success() || r.getData() == null) {
                return "";
            }
            Object v = r.getData().get(field);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            log.warn("选课资格-档案查询失败 userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private boolean compareField(String actual, String op, List<String> values) {
        String a = actual == null ? "" : actual.trim();
        if (values.isEmpty()) {
            return false;
        }
        if ("EQ".equalsIgnoreCase(op)) {
            return a.equalsIgnoreCase(values.get(0).trim());
        }
        if ("NE".equalsIgnoreCase(op)) {
            return !a.equalsIgnoreCase(values.get(0).trim());
        }
        for (String v : values) {
            if (v != null && a.equalsIgnoreCase(v.trim())) {
                return true;
            }
        }
        return false;
    }

    /** points_min：累计积分 ≥ value（数据源 lms-learning /learn/stats/my） */
    private Result checkPoints(Long userId, Item it) {
        Integer min = it.value();
        if (min == null) {
            return new Result(false, "积分规则缺少下限值");
        }
        try {
            var r = learningClient.myStats();
            if (r == null || !r.success() || r.getData() == null) {
                return new Result(false, "积分查询失败，请稍后再试");
            }
            double points = asDouble(r.getData().get("pointsTotal"));
            return new Result(points >= min,
                    "需累计积分 ≥ " + min + "（当前：" + (long) points + "）");
        } catch (Exception e) {
            log.warn("选课资格-积分查询失败 userId={}: {}", userId, e.getMessage());
            return new Result(false, "积分查询暂不可用，请稍后再试");
        }
    }

    /** enrolled：已选过目标课程（在读即可）；course_done：已选且学习进度 100% */
    private Result checkEnrolled(Long userId, Item it, boolean requireDone) {
        Long target = it.courseId();
        if (target == null) {
            return new Result(false, "规则缺少课程 id");
        }
        boolean enrolled = enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getCourseId, target)
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getStatus, EnrollmentStatus.ACTIVE.getValue())) > 0;
        if (!enrolled) {
            return new Result(false, "需先选过课程（id=" + target + "）");
        }
        if (!requireDone) {
            return new Result(true, "");
        }
        // course_done：学习进度 ≥100（lms-learning /lessons/learn/progress）
        try {
            var r = learningClient.courseProgress(target);
            if (r == null || !r.success() || r.getData() == null) {
                return new Result(false, "学习进度查询失败，请稍后再试");
            }
            double progress = extractProgress(r.getData());
            return new Result(progress >= 100,
                    "需修完课程（id=" + target + "，进度 100%）（当前：" + (long) progress + "%）");
        } catch (Exception e) {
            log.warn("选课资格-进度查询失败 userId={} courseId={}: {}", userId, target, e.getMessage());
            return new Result(false, "学习进度查询暂不可用，请稍后再试");
        }
    }

    /** 容错转数字（积分/进度字段可能为 Number 或数字字符串） */
    private double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** 容错取进度：支持 {"progress":88} 或直接数字等形态 */
    private double extractProgress(Map<String, Object> data) {
        double p = asDouble(data.get("progress"));
        if (p > 0) {
            return p;
        }
        for (Object v : data.values()) {
            if (v instanceof Number n && n.doubleValue() > 0) {
                return n.doubleValue();
            }
        }
        return 0;
    }
}
