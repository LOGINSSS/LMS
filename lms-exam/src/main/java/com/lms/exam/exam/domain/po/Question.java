package com.lms.exam.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lms.common.domain.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目实体
 *
 * 对应表 question。业务含义：题库核心数据，题型（单选/多选/判断）、难度、答案 JSON 按约定存储，
 * 可被多个业务（课程/考试）经 question_biz 引用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question")
public class Question extends BaseEntity {

    /** 题目 id（自增主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题干 */
    private String name;

    /** 题型：1单选 2多选 3判断，取值见 QuestionType 枚举 */
    private Integer type;

    /** 题目分类（如 微服务/Java/数据库） */
    private String category;

    /** 难度：1易 2中 3难，取值见 Difficulty 枚举 */
    private Integer difficulty;

    /** 答案解析 */
    private String analysis;

    /** 答案（JSON：单选 {"option":"A"} / 多选 {"options":["A","B"]} / 判断 {"judge":true}） */
    private String answer;

    /** 状态：1启用 0停用 */
    private Integer status;
}
