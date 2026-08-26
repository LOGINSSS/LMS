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
