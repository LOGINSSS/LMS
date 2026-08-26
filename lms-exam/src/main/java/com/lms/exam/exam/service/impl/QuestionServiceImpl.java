package com.lms.exam.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.exam.exam.constants.QuestionErrorInfo;
import com.lms.exam.exam.domain.dto.QuestionFormDTO;
import com.lms.exam.exam.domain.po.Question;
import com.lms.exam.exam.domain.po.QuestionBiz;
import com.lms.exam.exam.domain.query.QuestionPageQuery;
import com.lms.exam.exam.domain.vo.QuestionVO;
import com.lms.exam.exam.enums.Difficulty;
import com.lms.exam.exam.enums.QuestionType;
import com.lms.exam.exam.mapper.QuestionBizMapper;
import com.lms.exam.exam.mapper.QuestionMapper;
import com.lms.exam.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目业务服务实现
 *
 * 事务边界：建题/改题/绑定均为单事务，任一失败整体回滚。
 * 权限规则：题目管理仅限教师（userType=2），身份取自 UserContext（网关透传用户头）。
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements IQuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionBizMapper questionBizMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveQuestion(QuestionFormDTO dto) {
        //1. 权限校验：仅教师可建题，防止学生越权写入题库
        assertTeacher();
        //2. 校验题型与难度取值合法：非法枚举值拒绝入库
        AssertUtils.isTrue(QuestionType.of(dto.getType()) != null, "非法的题型");
        AssertUtils.isTrue(Difficulty.of(dto.getDifficulty()) != null, "非法的难度");
        //3. 组装落库：初始状态启用（status=1）
        Question question = BeanUtils.copyBean(dto, Question.class);
        question.setStatus(1);
        if (questionMapper.insert(question) <= 0) {
            throw new CommonException(QuestionErrorInfo.QUESTION_SAVE_FAILED);
        }
        return question.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        //1. 权限校验 + 题目存在性校验
        assertTeacher();
        Question question = getQuestionEntity(id);
        //2. 校验枚举取值（入参非 null 时才校验）
        if (dto.getType() != null) {
            AssertUtils.isTrue(QuestionType.of(dto.getType()) != null, "非法的题型");
        }
        if (dto.getDifficulty() != null) {
            AssertUtils.isTrue(Difficulty.of(dto.getDifficulty()) != null, "非法的难度");
        }
        //3. 组装更新对象：仅更新入参非 null 字段
        Question update = BeanUtils.copyBean(dto, Question.class);
        update.setId(question.getId());
        if (questionMapper.updateById(update) <= 0) {
            throw new CommonException(QuestionErrorInfo.QUESTION_SAVE_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id) {
        //1. 权限校验 + 题目存在性校验
        assertTeacher();
        getQuestionEntity(id);
        //2. 逻辑删除：BaseEntity.deleted 置 1，绑定关系保留（历史数据不破坏）
        questionMapper.deleteById(id);
    }

    @Override
    public QuestionVO getQuestion(Long id) {
        //1. 查询题目并转 VO
        return BeanUtils.copyBean(getQuestionEntity(id), QuestionVO.class);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1. 组装筛选条件：题型/分类/难度均可选
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (query.getType() != null) {
            wrapper.eq(Question::getType, query.getType());
        }
        if (query.getCategory() != null && !query.getCategory().isBlank()) {
            wrapper.eq(Question::getCategory, query.getCategory());
        }
        if (query.getDifficulty() != null) {
            wrapper.eq(Question::getDifficulty, query.getDifficulty());
        }
        //2. 分页查询并转 VO
        Page<Question> page = query.toMpPageDefaultSortByCreateTimeDesc();
        questionMapper.selectPage(page, wrapper);
        List<QuestionVO> vos = BeanUtils.copyList(page.getRecords(), QuestionVO.class);
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindToBiz(Long questionId, Long bizId, Integer score) {
        //1. 权限校验 + 题目存在性校验
        assertTeacher();
        getQuestionEntity(questionId);
        //2. 查绑定记录：存在则更新分值，不存在则插入
        //   【保障机制】幂等：uk_question_biz 唯一索引 + DuplicateKeyException 兜底并发
        QuestionBiz existed = questionBizMapper.selectOne(new LambdaQueryWrapper<QuestionBiz>()
                .eq(QuestionBiz::getQuestionId, questionId)
                .eq(QuestionBiz::getBizId, bizId));
        if (existed != null) {
            existed.setScore(score == null ? 0 : score);
            questionBizMapper.updateById(existed);
            return;
        }
        QuestionBiz bind = new QuestionBiz();
        bind.setQuestionId(questionId);
        bind.setBizId(bizId);
        bind.setScore(score == null ? 0 : score);
        try {
            questionBizMapper.insert(bind);
        } catch (DuplicateKeyException e) {
            // 并发兜底：撞唯一键说明已绑定，重查后更新分值
            QuestionBiz again = questionBizMapper.selectOne(new LambdaQueryWrapper<QuestionBiz>()
                    .eq(QuestionBiz::getQuestionId, questionId)
                    .eq(QuestionBiz::getBizId, bizId));
            if (again != null) {
                again.setScore(score == null ? 0 : score);
                questionBizMapper.updateById(again);
            }
        }
    }

    @Override
    public List<QuestionVO> queryByBizId(Long bizId) {
        //1. 查业务下的绑定记录
        List<QuestionBiz> binds = questionBizMapper.selectList(new LambdaQueryWrapper<QuestionBiz>()
                .eq(QuestionBiz::getBizId, bizId));
        if (CollUtils.isEmpty(binds)) {
            return Collections.emptyList();
        }
        //2. 取题目 id 集合，批量查启用题目
        List<Long> questionIds = binds.stream().map(QuestionBiz::getQuestionId).collect(Collectors.toList());
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds)
                .eq(Question::getStatus, 1));
        //3. 转 VO 返回
        return BeanUtils.copyList(questions, QuestionVO.class);
    }

    /**
     * 校验当前用户为教师（非教师抛 Forbidden）
     */
    private void assertTeacher() {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可管理题目");
        }
    }

    /**
     * 查询题目实体（不存在抛业务异常）
     */
    private Question getQuestionEntity(Long id) {
        Question question = questionMapper.selectById(id);
        AssertUtils.notNull(question, QuestionErrorInfo.QUESTION_NOT_FOUND.getMsg());
        return question;
    }
}
