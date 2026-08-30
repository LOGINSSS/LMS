package com.lms.kb.knowledge.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAGAS 评估样本实体
 *
 * 对应表 rag_eval_sample。每次 RAG 问答把链路中间产物与结果快照落库，
 * 供导出为 ragas 评估数据集（question/answer/contexts/ground_truth）离线量化。
 */
@Data
@TableName("rag_eval_sample")
public class RagEvalSample {

    /** 样本 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 id */
    private Long courseId;

    /** 用户问题 */
    private String question;

    /** rewrite 节点改写后的查询 */
    private String rewritten;

    /** HyDE 节点假设性回答 */
    private String hyde;

    /** 精排后上下文 JSON（[{text,source,doc_type,score}]） */
    private String contexts;

    /** 生成答案 */
    private String answer;

    /** 标准答案（人工标注，评估用） */
    private String groundTruth;

    /** 答案来源 JSON（[{source,doc_type,text}]） */
    private String sources;

    /** 创建时间 */
    private LocalDateTime createTime;
}
