import request from './request'

// 课程卡片分页（已发布）：返回 { total, list }
export const queryCoursePage = (params) => request.get('/courses/page', { params })

// 课程卡片详情
export const getCourseDetail = (id) => request.get(`/courses/${id}`)

// 学生选课
export const enrollCourse = (id) => request.post(`/courses/${id}/enroll`)

// 学生退课
export const quitCourse = (id) => request.post(`/courses/${id}/quit`)

// 学生：我选过的课程
export const queryEnrolledCourses = (params) => request.get('/courses/enrolled', { params })

// 教师：添加课程
export const addCourse = (data) => request.post('/admin/courses', data)

// 教师：修改课程（仅本人）
export const updateCourse = (id, data) => request.put(`/admin/courses/${id}`, data)

// 教师：上下架课程（status 1 发布 / 0 下架）
export const changeCourseStatus = (id, status) => request.put(`/admin/courses/${id}/status`, null, { params: { status } })

// 教师：我的课程（含未发布）
export const queryMyCourses = (params) => request.get('/admin/courses/mine', { params })
