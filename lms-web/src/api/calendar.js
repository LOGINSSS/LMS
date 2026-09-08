import request from './request'

// 统一日历事件（排课 class / 考试 exam / 作业 assignment；start/end yyyy-MM-dd 含端点）
export const mineCalendar = (params) => request.get('/calendar/mine', { params })
