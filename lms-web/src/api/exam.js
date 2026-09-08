import request from './request'

// 教师新增题目
export const addQuestion = (data) => request.post('/admin/questions', data)

// 教师修改题目
export const updateQuestion = (id, data) => request.put(`/admin/questions/${id}`, data)

// 教师删除题目
export const deleteQuestion = (id) => request.delete(`/admin/questions/${id}`)

// 题目分页（type/category/difficulty 筛选）
export const queryQuestionPage = (params) => request.get('/admin/questions/page', { params })

// 题目绑定业务（bizType: 1课程 2章节 3考试卷，可设分值）
export const bindQuestionBiz = (id, bizType, bizId, score = 0) =>
  request.post(`/admin/questions/${id}/biz`, null, { params: { bizType, bizId, score } })

// 按业务取题（bizType: 1课程 2章节 3考试卷）
export const queryQuestionsByBiz = (bizType, bizId) => request.get(`/questions/biz/${bizType}/${bizId}`)

// ---------- 发布物排期（作业/考试，v1 收尾 rails） ----------

// 我的考试/作业列表（courseIds=已报名课程逗号串；bizType 1考试 2作业 可空）
export const listMineSchedules = (params) => request.get('/exam-schedules/mine', { params })

// 排期详情
export const getSchedule = (id) => request.get(`/exam-schedules/${id}`)

// ---------- 试卷快照（答题） ----------

// 卷面学生视图（已剥离答案/解析）
export const getPaper = (id) => request.get(`/exam-papers/${id}`)

// 同步交卷（作业：即交即判、可重做）
export const submitSchedule = (id, data) => request.post(`/exam-schedules/${id}/submit`, data)

// 考试异步提交（锁页 → Kafka 幂等消费端判分），返回 { submissionId }
export const submitScheduleAsync = (id, data) => request.post(`/exam-schedules/${id}/submit-async`, data)

// ---------- 教师端：组卷工作台（出卷流程骨干） ----------

// 组卷（题库选题 → 快照成卷，草稿态）items:[{id,score}]
export const createPaper = (data) => request.post('/admin/exam-papers', data)

// 发布卷面（owner）
export const publishPaper = (id) => request.post(`/admin/exam-papers/${id}/publish`)

// 我组的卷列表（教师，含答案）
export const queryMyPapers = () => request.get('/admin/exam-papers')

// 卷面管理视图（含答案/解析）
export const getPaperAdmin = (id) => request.get(`/admin/exam-papers/${id}`)

// 我发布的排期列表（老师）
export const queryMySchedules = () => request.get('/admin/exam-schedules')

// 发布排期（作业 bizType=2 / 考试 bizType=1，挂已发布卷面）
export const publishSchedule = (data) => request.post('/admin/exam-schedules', data)

// 结束排期（发布者本人）
export const closeSchedule = (id) => request.post(`/admin/exam-schedules/${id}/close`)
