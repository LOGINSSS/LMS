import request from './request'

// 课程分类标签列表（全局共享；教师建课/学生筛选下拉）
export const listCategories = () => request.get('/courses/categories')

// 新增课程分类（教师；所有教师可见）
export const addCategory = (name) => request.post('/admin/courses/categories', { name })

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

// 教师：删除课程（级联选课/目录/章节 + 清理该课程 RAG 知识库）
export const deleteCourse = (id) => request.delete(`/admin/courses/${id}`)

// 教师：修改课程（仅本人）
export const updateCourse = (id, data) => request.put(`/admin/courses/${id}`, data)

// 教师：上下架课程（status 1 发布 / 0 下架）
export const changeCourseStatus = (id, status) => request.put(`/admin/courses/${id}/status`, null, { params: { status } })

// 教师：我的课程（含未发布）
export const queryMyCourses = (params) => request.get('/admin/courses/mine', { params })

// 课程目录树（左栏大纲）
export const getCourseCatalog = (id) => request.get(`/courses/${id}/catalog`)

// 章节正文（右栏 markdown）
export const getChapterContent = (catalogId) => request.get(`/catalog/${catalogId}/chapter`)

// 教师：新增章节（章或节）
export const addCatalogNode = (courseId, data) => request.post(`/admin/courses/${courseId}/catalog`, data)

// 教师：修改章节
export const updateCatalogNode = (catalogId, data) => request.put(`/admin/catalog/${catalogId}`, data)

// 教师：删除章节（含子节与正文）
export const deleteCatalogNode = (catalogId) => request.delete(`/admin/catalog/${catalogId}`)

// 教师：保存章节正文（markdown）
export const saveChapterContent = (catalogId, data) => request.put(`/admin/catalog/${catalogId}/chapter`, data)

// 教师：提交课程发布（设置抢课窗口）
export const publishCourse = (id, params) => request.put(`/admin/courses/${id}/publish`, null, { params })

// 学生：抢课（Redis 预检 + Kafka 异步落库）
export const grabCourse = (id) => request.post(`/grab/${id}`)

// 抢课状态（窗口/剩余库存/是否已抢）
export const getGrabStatus = (id) => request.get(`/grab/${id}/status`)
