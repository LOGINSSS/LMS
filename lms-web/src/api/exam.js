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
