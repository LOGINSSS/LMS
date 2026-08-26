package com.lms.course.course.service;

import com.lms.common.domain.dto.PageDTO;
import com.lms.course.course.domain.dto.CourseCardVO;
import com.lms.course.course.domain.dto.CourseFormDTO;
import com.lms.course.course.domain.query.CoursePageQuery;

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
     * 教师上下架自己创建的课程
     *
     * @param id     课程 id
     * @param status 目标状态（0 下架 / 1 发布），取值见 CourseStatus 枚举
     * @throws CommonException 课程不存在、非本人课程
     */
    void changeStatus(Long id, Integer status);

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
}
