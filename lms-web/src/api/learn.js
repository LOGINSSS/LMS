import request from './request'

// ---------- 课次 ----------
export const listLessons = (courseId) => request.get('/lessons', { params: { courseId } })
export const getLesson = (id) => request.get(`/lessons/${id}`)
export const addLesson = (data) => request.post('/lessons/admin/lessons', data)

// ---------- 学习记录 ----------
export const recordLearning = (data) => request.post('/lessons/learn/records', data)
export const getCourseProgress = (courseId) => request.get('/lessons/learn/progress', { params: { courseId } })

// ---------- 笔记 ----------
export const addNote = (data) => request.post('/notes', data)
export const listNotes = (params) => request.get('/notes/page', { params })
export const updateNote = (id, data) => request.put(`/notes/${id}`, data)
export const deleteNote = (id) => request.delete(`/notes/${id}`)

// ---------- 互动问答 ----------
export const askQuestion = (data) => request.post('/qa/questions', data)
export const listQuestions = (params) => request.get('/qa/questions/page', { params })
export const answerQuestion = (id, data) => request.post(`/qa/questions/${id}/answers`, data)

// ---------- 签到与积分 ----------
export const signIn = () => request.post('/points/sign-in')
export const myPoints = (params) => request.get('/points/records', { params })
export const pointsBoard = (params) => request.get('/points/board', { params })

// ---------- 0.2 课程积分/签到/阅读 ----------
// 课程页签到（课程维度，判断今天/下一天）
export const signInCourse = (courseId) => request.post(`/points/courses/${courseId}/sign-in`)
// 课程积分实时榜 TopN（ZSET）
export const coursePointsBoard = (courseId, params) => request.get(`/points/courses/${courseId}/board`, { params })
// 我的课程积分与排名（ZSET）
export const myCoursePoints = (courseId) => request.get(`/points/courses/${courseId}/me`)
// 上报章节阅读（首次阅读发积分，幂等）
export const reportChapterRead = (courseId, catalogId) =>
  request.post(`/points/courses/${courseId}/chapters/${catalogId}/read`)

// ---------- 我的学习统计（首页学习概览）----------
export const myLearnStats = () => request.get('/learn/stats/my')

// 作业/考试结果统计（来源 3 作业 / 4 考试）：题量/答对/得分 + 最近记录
export const homeworkExamStats = () => request.get('/learn/stats/homework-exam')

// ---------- 站内消息信箱（QA 答疑通知）----------
// 我的消息分页（含已读/未读）
export const listMyNotifications = (params) => request.get('/qa/notifications/page', { params })

// 我的未读数（顶栏红点）
export const unreadNotifyCount = () => request.get('/qa/notifications/unread-count')

// 标记已读（ids 数组）
export const markNotifyRead = (ids) => request.put('/qa/notifications/read', null, { params: { ids: ids.join(',') } })

// 全部标记已读
export const markAllNotifyRead = () => request.put('/qa/notifications/read-all', null)
