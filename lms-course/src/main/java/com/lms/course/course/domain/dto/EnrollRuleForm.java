package com.lms.course.course.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 选课资格规则表单（老师发布课程时框定可选用户范围）
 *
 * mode：ALL=全部满足 / ANY=任一满足；
 * rules 元素字段按 type 取用（见下），未用字段忽略。
 */
@Data
@Schema(description = "选课资格规则表单")
public class EnrollRuleForm {

    /** 组合模式：ALL（默认，全部满足）/ ANY（任一满足） */
    @Schema(description = "组合模式：ALL=全部满足 ANY=任一满足")
    private String mode = "ALL";

    @Schema(description = "规则列表")
    private List<RuleItem> rules = new ArrayList<>();

    /** 单条规则 */
    @Data
    @Schema(description = "单条规则")
    public static class RuleItem {

        /**
         * 规则类型：
         * grade=学生年级（如 大三）；major=专业；college=学院；
         * points_min=累计积分下限（value）；enrolled=已选过课程（courseId，在读即可）；
         * course_done=已修完课程（courseId，需学习进度 100%）
         */
        @Schema(description = "规则类型：grade/major/college/points_min/enrolled/course_done")
        private String type;

        /** 比较符：IN（默认，values 含即满足）/ EQ / NE（仅档案类字段使用） */
        @Schema(description = "比较符：IN/EQ/NE（档案字段），默认 IN")
        private String op = "IN";

        /** 期望值集合（grade/major/college 用，如 ["大三"]） */
        @Schema(description = "期望值集合")
        private List<String> values = new ArrayList<>();

        /** 课程 id（enrolled/course_done 用：要求已选/已修完的课程） */
        @Schema(description = "课程 id（enrolled/course_done 用）")
        private Long courseId;

        /** 数值（points_min 用：积分下限） */
        @Schema(description = "数值（points_min 用）")
        private Integer value;
    }
}
