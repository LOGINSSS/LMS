package com.lms.learning.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lms.common.domain.dto.PageDTO;
import com.lms.common.enums.UserType;
import com.lms.common.exceptions.CommonException;
import com.lms.common.exceptions.ForbiddenException;
import com.lms.common.utils.AssertUtils;
import com.lms.common.utils.BeanUtils;
import com.lms.common.utils.CollUtils;
import com.lms.common.utils.UserContext;
import com.lms.learning.learning.constants.LearningErrorInfo;
import com.lms.learning.learning.domain.dto.AnswerFormDTO;
import com.lms.learning.learning.domain.dto.LearningRecordFormDTO;
import com.lms.learning.learning.domain.dto.LessonFormDTO;
import com.lms.learning.learning.domain.dto.NoteFormDTO;
import com.lms.learning.learning.domain.dto.QaQuestionFormDTO;
import com.lms.learning.learning.domain.po.Answer;
import com.lms.learning.learning.domain.po.LearningRecord;
import com.lms.learning.learning.domain.po.Lesson;
import com.lms.learning.learning.domain.po.Note;
import com.lms.learning.learning.domain.po.PointsRecord;
import com.lms.learning.learning.domain.po.QaQuestion;
import com.lms.learning.learning.domain.po.SignIn;
import com.lms.learning.learning.domain.query.NotePageQuery;
import com.lms.learning.learning.domain.query.QaPageQuery;
import com.lms.learning.learning.domain.vo.LessonVO;
import com.lms.learning.learning.domain.vo.NoteVO;
import com.lms.learning.learning.domain.vo.PointsBoardVO;
import com.lms.learning.learning.domain.vo.PointsVO;
import com.lms.learning.learning.domain.vo.QaAnswerVO;
import com.lms.learning.learning.domain.vo.QaQuestionVO;
import com.lms.learning.learning.enums.PointsType;
import com.lms.learning.learning.mapper.AnswerMapper;
import com.lms.learning.learning.mapper.LearningRecordMapper;
import com.lms.learning.learning.mapper.LessonMapper;
import com.lms.learning.learning.mapper.NoteMapper;
import com.lms.learning.learning.mapper.PointsRecordMapper;
import com.lms.learning.learning.mapper.QaQuestionMapper;
import com.lms.learning.learning.mapper.SignInMapper;
import com.lms.learning.learning.service.ILearningService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习过程业务服务实现
 *
 * 事务边界：签到/学习/提问/回答（含积分发放）均为单事务，任一失败整体回滚；
 * 积分发放规则见 PointsType（签到 5 / 学习 2 / 提问 3 / 回答 5 / 被采纳 10）。
 * 身份来源：UserContext（网关透传用户头），建课次限教师，其余登录即可。
 */
@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements ILearningService {

    private final LessonMapper lessonMapper;
    private final LearningRecordMapper recordMapper;
    private final NoteMapper noteMapper;
    private final QaQuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final PointsRecordMapper pointsMapper;
    private final SignInMapper signInMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addLesson(LessonFormDTO dto) {
        //1. 权限校验：仅教师可建课次
        assertTeacher();
        //2. 组装落库：sort 默认 0
        Lesson lesson = BeanUtils.copyBean(dto, Lesson.class);
        if (lesson.getSort() == null) {
            lesson.setSort(0);
        }
        lessonMapper.insert(lesson);
        return lesson.getId();
    }

    @Override
    public List<LessonVO> listLessons(Long courseId) {
        //1. 按课程查课次并按 sort 升序
        List<Lesson> lessons = lessonMapper.selectList(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getCourseId, courseId)
                .orderByAsc(Lesson::getSort));
        return BeanUtils.copyList(lessons, LessonVO.class);
    }

    @Override
    public LessonVO getLesson(Long id) {
        return BeanUtils.copyBean(getLessonEntity(id), LessonVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordLearning(LearningRecordFormDTO dto) {
        //1. 登录校验并取当前用户
        Long userId = currentUserId();
        //2. 课次存在性校验并取课程 id
        Lesson lesson = getLessonEntity(dto.getLessonId());
        int progress = dto.getProgress() == null ? 0 : dto.getProgress();
        //3. 按 uk_user_lesson 查记录：存在则合并进度，不存在则插入（首次学习发放积分）
        LearningRecord record = recordMapper.selectOne(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getLessonId, dto.getLessonId()));
        if (record != null) {
            // 已学过：进度取较大值并刷新学习时间
            record.setProgress(Math.max(record.getProgress(), progress));
            record.setLastLearnTime(LocalDateTime.now());
            recordMapper.updateById(record);
            return;
        }
        //4. 首次学习：插入记录并发放学习积分
        LearningRecord insert = new LearningRecord();
        insert.setUserId(userId);
        insert.setCourseId(lesson.getCourseId());
        insert.setLessonId(dto.getLessonId());
        insert.setProgress(progress);
        insert.setLastLearnTime(LocalDateTime.now());
        recordMapper.insert(insert);
        grantPoints(userId, PointsType.LEARN);
    }

    @Override
    public int getCourseProgress(Long courseId) {
        Long userId = currentUserId();
        //1. 统计课程总课次数与已学课次数
        Long totalLessons = lessonMapper.selectCount(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getCourseId, courseId));
        if (totalLessons == null || totalLessons == 0) {
            return 0;
        }
        Long learned = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getCourseId, courseId));
        //2. 计算百分比
        return (int) (learned * 100L / totalLessons);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addNote(NoteFormDTO dto) {
        //1. 登录校验
        Long userId = currentUserId();
        //2. 组装落库（作者当前用户）
        Note note = BeanUtils.copyBean(dto, Note.class);
        note.setUserId(userId);
        noteMapper.insert(note);
        return note.getId();
    }

    @Override
    public PageDTO<NoteVO> listNotes(NotePageQuery query) {
        //1. 按课程分页查笔记，按创建时间倒序
        Page<Note> page = query.toMpPageDefaultSortByCreateTimeDesc();
        noteMapper.selectPage(page, new LambdaQueryWrapper<Note>()
                .eq(query.getCourseId() != null, Note::getCourseId, query.getCourseId()));
        List<NoteVO> vos = BeanUtils.copyList(page.getRecords(), NoteVO.class);
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNote(Long id, NoteFormDTO dto) {
        //1. 校验笔记存在且为本人
        Note note = getOwnNote(id);
        //2. 更新内容（仅 content）
        Note update = new Note();
        update.setId(note.getId());
        update.setContent(dto.getContent());
        noteMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNote(Long id) {
        //1. 校验笔记存在且为本人
        getOwnNote(id);
        //2. 逻辑删除
        noteMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long askQuestion(QaQuestionFormDTO dto) {
        //1. 登录校验
        Long userId = currentUserId();
        //2. 组装落库并发放提问积分
        QaQuestion question = BeanUtils.copyBean(dto, QaQuestion.class);
        question.setUserId(userId);
        questionMapper.insert(question);
        grantPoints(userId, PointsType.QUESTION);
        return question.getId();
    }

    @Override
    public PageDTO<QaQuestionVO> listQuestions(QaPageQuery query) {
        //1. 按课程分页查问题，按创建时间倒序
        Page<QaQuestion> page = query.toMpPageDefaultSortByCreateTimeDesc();
        questionMapper.selectPage(page, new LambdaQueryWrapper<QaQuestion>()
                .eq(query.getCourseId() != null, QaQuestion::getCourseId, query.getCourseId()));
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.of(page.getTotal(), Collections.emptyList());
        }
        //2. 批量查回答：按 questionId 集合一次查全，避免 N+1
        List<Long> questionIds = page.getRecords().stream().map(QaQuestion::getId).collect(Collectors.toList());
        List<Answer> answers = answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                .in(Answer::getQuestionId, questionIds)
                .orderByAsc(Answer::getCreateTime));
        Map<Long, List<QaAnswerVO>> answerMap = answers.stream()
                .collect(Collectors.groupingBy(Answer::getQuestionId,
                        Collectors.mapping(a -> BeanUtils.copyBean(a, QaAnswerVO.class), Collectors.toList())));
        //3. 组装问题 VO（含回答列表）
        List<QaQuestionVO> vos = page.getRecords().stream().map(q -> {
            QaQuestionVO vo = BeanUtils.copyBean(q, QaQuestionVO.class);
            vo.setAnswers(answerMap.getOrDefault(q.getId(), Collections.emptyList()));
            return vo;
        }).collect(Collectors.toList());
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long answerQuestion(Long questionId, AnswerFormDTO dto) {
        //1. 登录校验
        Long userId = currentUserId();
        //2. 问题存在性校验
        QaQuestion question = questionMapper.selectById(questionId);
        AssertUtils.notNull(question, LearningErrorInfo.QUESTION_NOT_FOUND.getMsg());
        //3. 组装落库并发放回答积分
        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setContent(dto.getContent());
        answer.setAccepted(0);
        answerMapper.insert(answer);
        grantPoints(userId, PointsType.ANSWER);
        return answer.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signIn() {
        //1. 登录校验
        Long userId = currentUserId();
        LocalDate today = LocalDate.now();
        //2. 幂等预校验：今日已签到直接拒绝
        //   【保障机制】uk_user_date 唯一索引 + DuplicateKeyException 并发兜底
        Long exists = signInMapper.selectCount(new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getUserId, userId)
                .eq(SignIn::getSignDate, today));
        if (exists != null && exists > 0) {
            throw new CommonException(LearningErrorInfo.ALREADY_SIGNED);
        }
        //3. 落库签到记录并发放签到积分
        SignIn signIn = new SignIn();
        signIn.setUserId(userId);
        signIn.setSignDate(today);
        try {
            signInMapper.insert(signIn);
        } catch (DuplicateKeyException e) {
            throw new CommonException(LearningErrorInfo.ALREADY_SIGNED);
        }
        grantPoints(userId, PointsType.SIGN_IN);
    }

    @Override
    public PageDTO<PointsVO> myPoints(int pageNo, int pageSize) {
        Long userId = currentUserId();
        //1. 分页查当前用户积分流水（时间倒序）
        Page<PointsRecord> page = Page.of(pageNo, pageSize);
        pointsMapper.selectPage(page, new LambdaQueryWrapper<PointsRecord>()
                .eq(PointsRecord::getUserId, userId)
                .orderByDesc(PointsRecord::getCreateTime));
        List<PointsVO> vos = BeanUtils.copyList(page.getRecords(), PointsVO.class);
        return PageDTO.of(page.getTotal(), vos);
    }

    @Override
    public List<PointsBoardVO> pointsBoard(int size) {
        //1. 按用户聚合积分总分，取 Top N（练手直接 SQL 聚合，数据量小无压力）
        int safeSize = size < 1 ? 10 : Math.min(size, 50);
        List<Map<String, Object>> rows = pointsMapper.selectMaps(new QueryWrapper<PointsRecord>()
                .select("user_id", "SUM(points) as total")
                .groupBy("user_id")
                .orderByDesc("total")
                .last("LIMIT " + safeSize));
        //2. 转出参
        return rows.stream()
                .map(row -> new PointsBoardVO(
                        ((Number) row.get("user_id")).longValue(),
                        ((Number) row.get("total")).longValue()))
                .collect(Collectors.toList());
    }

    /**
     * 发放积分：插入积分流水（单事务内与业务操作同生共死）
     */
    private void grantPoints(Long userId, PointsType type) {
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setType(type.getValue());
        record.setPoints(type.getPoints());
        pointsMapper.insert(record);
    }

    /**
     * 校验登录并返回当前用户 id
     */
    private Long currentUserId() {
        Long userId = UserContext.getUser();
        AssertUtils.isNotNull(userId, "请先登录");
        return userId;
    }

    /**
     * 校验当前用户为教师（建课次专用）
     */
    private void assertTeacher() {
        Integer userType = UserContext.getUserType();
        Long userId = UserContext.getUser();
        if (userId == null || userType == null || userType != UserType.TEACHER.getValue()) {
            throw new ForbiddenException("仅教师可创建课次");
        }
    }

    /**
     * 查询课次实体（不存在抛业务异常）
     */
    private Lesson getLessonEntity(Long id) {
        Lesson lesson = lessonMapper.selectById(id);
        AssertUtils.notNull(lesson, LearningErrorInfo.LESSON_NOT_FOUND.getMsg());
        return lesson;
    }

    /**
     * 查询本人笔记（不存在或非本人抛业务异常）
     */
    private Note getOwnNote(Long id) {
        Note note = noteMapper.selectById(id);
        AssertUtils.notNull(note, LearningErrorInfo.NOTE_NOT_FOUND.getMsg());
        if (!note.getUserId().equals(UserContext.getUser())) {
            throw new ForbiddenException("只能操作自己的笔记");
        }
        return note;
    }

    @Override
    public long countTodaySignIn() {
        //1. 统计今日签到记录数（数据中心看板口径）
        Long count = signInMapper.selectCount(new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getSignDate, LocalDate.now()));
        return count == null ? 0L : count;
    }

    @Override
    public long countTodayLearn() {
        //1. 统计今日学习人次：最近学习时间落在今日 0 点之后（含）
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .ge(LearningRecord::getLastLearnTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0L : count;
    }

    @Override
    public long countLearnTotal() {
        //1. 统计学习记录总数（数据中心看板口径）
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<>());
        return count == null ? 0L : count;
    }
}
