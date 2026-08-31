package com.lms.exam.exam.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.exam.exam.domain.dto.QuestionFormDTO;
import com.lms.exam.exam.domain.query.QuestionPageQuery;
import com.lms.exam.exam.domain.vo.QuestionVO;

import java.util.List;

/**
 * 题目业务服务
 *
 * 承载题目增删改查、题目与业务绑定、按业务取题；
 * 权限约定：题目管理（建/改/删/绑）仅限教师。
 * 0.2 扩展：绑定维度从「仅 bizId」升级为「bizType + bizId」（1 课程 / 2 章节 / 3 考试卷）。
 */
public interface IQuestionService {

    /**
     * 教师新增题目
     *
     * @param dto 题目表单（题干/题型/难度必填）
     * @return 新题目 id
     */
    Long saveQuestion(QuestionFormDTO dto);

    /**
     * 教师修改题目（仅更新入参非 null 字段）
     *
     * @param id  题目 id
     * @param dto 题目表单
     */
    void updateQuestion(Long id, QuestionFormDTO dto);

    /**
     * 教师删除题目（逻辑删除）
     *
     * @param id 题目 id
     */
    void deleteQuestion(Long id);

    /**
     * 题目详情
     *
     * @param id 题目 id
     * @return 题目信息
     */
    QuestionVO getQuestion(Long id);

    /**
     * 题目分页（按题型/分类/难度筛选）
     *
     * @param query 分页参数
     * @return 题目分页结果
     */
    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    /**
     * 教师把题目绑定到业务（课程/章节/考试卷），可设分值；重复绑定更新分值
     *
     * @param questionId 题目 id
     * @param bizType    业务类型（1 课程 / 2 章节 / 3 考试卷）
     * @param bizId      业务 id
     * @param score      分值（默认 0）
     */
    void bindToBiz(Long questionId, Integer bizType, Long bizId, Integer score);

    /**
     * 按业务取题（考试/练习用，只含启用题）
     *
     * @param bizType 业务类型（1 课程 / 2 章节 / 3 考试卷）
     * @param bizId   业务 id
     * @return 启用题目列表
     */
    List<QuestionVO> queryByBiz(Integer bizType, Long bizId);
}
