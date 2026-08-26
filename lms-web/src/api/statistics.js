import request from './request'

// 数据看板总览
export const getDashboard = () => request.get('/dashboard')

// 今日数据
export const getTodayStats = () => request.get('/dashboard/today')

// 热门课程 Top N
export const getTopCourses = (size = 10) => request.get('/dashboard/top/courses', { params: { size } })

// 积分榜 Top N
export const getTopPoints = (size = 10) => request.get('/dashboard/top/points', { params: { size } })
