import request from './request'

// 同步课程索引到 ES（管理端手动触发）
export const syncSearchIndex = () => request.post('/search/sync')

// ES 课程搜索（keyword 匹配名称/简介，category 精确筛选）
export const searchCourses = (params) => request.get('/search/courses', { params })

// 按兴趣标签推荐课程
export const recommendCourses = (size = 10) => request.get('/search/recommend', { params: { size } })

// 上报兴趣标签（权重累加）
export const recordInterest = (tag) => request.post('/interests/record', { tag })

// 我的兴趣标签
export const myInterests = () => request.get('/interests')
