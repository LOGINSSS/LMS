package com.lms.learning.learning.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 学情聚合视图（诊断智能体输入：历史学习行为/成绩/错题汇总，需求文档 §4.2）
 */
@Data
public class DiagnosisVO {

    /** 总做题数 */
    private Long totalExercises;

    /** 总答对数 */
    private Long totalCorrect;

    /** 整体正确率（0-100） */
    private Double accuracy;

    /** 活跃学习天数（按做题日期去重） */
    private Long activeDays;

    /** 各知识点掌握统计 */
    private List<KnowledgeStat> knowledgeStats;

    /** 薄弱知识点（正确率最低，诊断输出） */
    private List<String> weakPoints;

    /** 知识点掌握统计项 */
    @Data
    public static class KnowledgeStat {
        private String knowledgePoint;
        private Long total;
        private Long correct;
        private Double accuracy;
    }
}
