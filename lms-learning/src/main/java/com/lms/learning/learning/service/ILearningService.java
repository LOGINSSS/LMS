package com.lms.learning.learning.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.learning.learning.domain.dto.AnswerFormDTO;
import com.lms.learning.learning.domain.dto.LearningRecordFormDTO;
import com.lms.learning.learning.domain.dto.LessonFormDTO;
import com.lms.learning.learning.domain.dto.NoteFormDTO;
import com.lms.learning.learning.domain.dto.QaQuestionFormDTO;
import com.lms.learning.learning.domain.query.NotePageQuery;
import com.lms.learning.learning.domain.query.QaPageQuery;
import com.lms.learning.learning.domain.vo.LessonVO;
import com.lms.learning.learning.domain.vo.NoteVO;
import com.lms.learning.learning.domain.vo.PointsBoardVO;
import com.lms.learning.learning.domain.vo.PointsVO;
import com.lms.learning.learning.domain.vo.QaQuestionVO;

import java.util.List;

/**
 * 学习过程业务服务
 *
 * 承载课次管理、学习记录、笔记、互动问答、签到与积分（奖励发放）；
 * 权限约定：建课次仅限教师，其余登录用户可用。
 */
public interface ILearningService {

    /**
     * 教师新增课次
     *
     * @param dto 课次表单
     * @return 新课次 id
     */
    Long addLesson(LessonFormDTO dto);

    /**
     * 按课程查课次列表（按 sort 升序）
     *
     * @param courseId 课程 id
     * @return 课次列表
     */
    List<LessonVO> listLessons(Long courseId);

    /**
     * 课次详情
     *
     * @param id 课次 id
     * @return 课次信息
     */
    LessonVO getLesson(Long id);

    /**
     * 上报学习进度（首次学习发放学习积分，幂等合并）
     *
     * @param dto 学习进度（课次 id + 进度百分比）
     */
    void recordLearning(LearningRecordFormDTO dto);

    /**
     * 我的课程学习进度（已学课次 / 总课次）
     *
     * @param courseId 课程 id
     * @return 进度百分比（0-100，无课次返回 0）
     */
    int getCourseProgress(Long courseId);

    /**
     * 新增笔记（作者为当前用户）
     *
     * @param dto 笔记表单
     * @return 新笔记 id
     */
    Long addNote(NoteFormDTO dto);

    /**
     * 按课程分页查笔记
     *
     * @param query 分页参数（courseId 必传）
     * @return 笔记分页
     */
    PageDTO<NoteVO> listNotes(NotePageQuery query);

    /**
     * 修改自己的笔记
     *
     * @param id  笔记 id
     * @param dto 笔记表单（content 更新）
     */
    void updateNote(Long id, NoteFormDTO dto);

    /**
     * 删除自己的笔记（逻辑删除）
     *
     * @param id 笔记 id
     */
    void deleteNote(Long id);

    /**
     * 发布提问（发放提问积分）
     *
     * @param dto 提问表单
     * @return 新问题 id
     */
    Long askQuestion(QaQuestionFormDTO dto);

    /**
     * 按课程分页查问题（含各问题回答列表）
     *
     * @param query 分页参数（courseId 必传）
     * @return 问题分页
     */
    PageDTO<QaQuestionVO> listQuestions(QaPageQuery query);

    /**
     * 回答问题（发放回答积分）
     *
     * @param questionId 问题 id
     * @param dto        回答表单
     * @return 新回答 id
     */
    Long answerQuestion(Long questionId, AnswerFormDTO dto);

    /**
     * 每日签到（一天一次，发放签到积分）
     */
    void signIn();

    /**
     * 我的积分明细（分页，按时间倒序）
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 积分记录分页
     */
    PageDTO<PointsVO> myPoints(int pageNo, int pageSize);

    /**
     * 积分榜 Top N（按积分总分降序）
     *
     * @param size 返回条数
     * @return 榜单条目列表
     */
    List<PointsBoardVO> pointsBoard(int size);
}
