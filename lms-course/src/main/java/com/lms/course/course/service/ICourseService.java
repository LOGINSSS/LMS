package com.lms.course.course.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.dto.CourseFormDTO;
import com.lms.course.course.domain.dto.EnrollRuleForm;
import com.lms.course.course.domain.query.CoursePageQuery;
import com.lms.course.course.domain.vo.CourseTopVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 课程业务服务
 *
 * 承载课程卡片展示、教师建课/管理、学生选课/退课核心业务；
 * 权限约定：建课与课程管理仅限教师本人，选课/退课仅限学生（身份取自 UserContext）。
 */
public interface ICourseService {

    /**
     * 教师添加课程
     *
     * @param dto 课程表单（名称必填，其余可选）
     * @return 新课程 id
     * @throws CommonException 当前用户非教师（Forbidden）
     */
    Long addCourse(CourseFormDTO dto);

    /**
     * 教师修改自己创建的课程（仅更新入参非 null 字段）
     *
     * @param id  课程 id
     * @param dto 课程表单
     * @throws CommonException 课程不存在（COURSE_NOT_FOUND）、非本人课程（Forbidden）
     */
    void updateCourse(Long id, CourseFormDTO dto);

    /**
     * 教师删除自己创建的课程（级联删除选课/目录/章节，并清理 Python RAG 该课程知识库）
     *
     * @param id 课程 id
     * @throws CommonException 课程不存在（COURSE_NOT_FOUND）、非本人课程（Forbidden）
     */
    void deleteCourse(Long id);

    /**
     * 教师上下架自己创建的课程
     *
     * @param id     课程 id
     * @param status 目标状态，取值见 CourseStatus 枚举
     * @throws CommonException 课程不存在、非本人课程
     */
    void changeStatus(Long id, Integer status);

    /**
     * 教师提交课程发布（进入待发布，抢课窗口到点后自动流转抢课中）
     *
     * @param id            课程 id
     * @param grabStartTime 抢课开始时间
     * @param grabEndTime   抢课结束时间
     * @param stock         抢课名额（0=不限）
     * @throws CommonException 课程不存在、非本人课程、窗口时间不合法
     */
    void publish(Long id, LocalDateTime grabStartTime, LocalDateTime grabEndTime, Integer stock);

    /** 读取课程选课资格规则（教师；无规则返回 null=不限选） */
    String getEnrollRule(Long courseId);

    /** 保存课程选课资格规则（教师本人课程；rules 空 = 不限选） */
    void saveEnrollRule(Long courseId, EnrollRuleForm form);

    /** 删除课程选课资格规则（不限选） */
    void removeEnrollRule(Long courseId);

    /**
     * 前台课程卡片分页（只展示已发布课程，附实时选课人数）
     *
     * @param query 分页参数（分类/关键字筛选可选）
     * @return 课程卡片分页结果
     */
    PageDTO<CourseCardVO> queryPublishedPage(CoursePageQuery query);

    /**
     * 前台课程卡片详情（只返回已发布课程）
     *
     * @param id 课程 id
     * @return 课程卡片详情
     * @throws CommonException 课程不存在或未发布
     */
    CourseCardVO getCourseDetail(Long id);

    /**
     * 教师查看自己创建的课程（含未发布，管理视角）
     *
     * @param query 分页参数
     * @return 课程卡片分页结果
     */
    PageDTO<CourseCardVO> queryMyCourses(CoursePageQuery query);

    /**
     * 学生选课（幂等：同一课程重复选课报 ALREADY_ENROLLED）
     *
     * @param courseId 课程 id
     * @throws CommonException 非学生（Forbidden）、课程未发布、重复选课
     */
    void enroll(Long courseId);

    /**
     * 学生退课（选课记录状态置为已退课，保留历史）
     *
     * @param courseId 课程 id
     * @throws CommonException 非学生（Forbidden）、未选该课程
     */
    void quit(Long courseId);

    /**
     * 学生查看自己选过的课程（选课中状态）
     *
     * @param query 分页参数
     * @return 课程卡片分页结果
     */
    PageDTO<CourseCardVO> queryEnrolledCourses(CoursePageQuery query);

    /**
     * 选课人次（选课中记录总数，数据中心看板用）
     *
     * @return 选课中记录数
     */
    long countEnrollTotal();

    /**
     * 今日新增课程数（数据中心看板用，统计口径为 create_time 落在今日 0 点之后）
     *
     * @return 今日新增课程数
     */
    long countTodayCourses();

    /**
     * 热门课程 Top N（按选课中人数降序，数据中心看板用）
     *
     * @param size 返回条数
     * @return 热门课程列表（含课程名与选课人数）
     */
    List<CourseTopVO> topCourses(int size);

    /**
     * 课程归属信息（供学习服务发答疑通知等内部场景使用，登录即可访问）
     *
     * @param id 课程 id
     * @return {teacherId, name}
     * @throws CommonException 课程不存在（COURSE_NOT_FOUND）
     */
    Map<String, Object> getCourseOwner(Long id);
}
