import request from './request'

// 点赞/取消点赞（切换，返回 { liked, likeCount }）
export const toggleLike = (bizType, bizId) => request.post(`/likes/${bizType}/${bizId}`)

// 点赞总数
export const likeCount = (bizType, bizId) => request.get(`/likes/count/${bizType}/${bizId}`)

// 当前用户点赞状态
export const likeStatus = (bizType, bizId) => request.get(`/likes/status/${bizType}/${bizId}`)

// 批量点赞状态（列表页用，bizIds 逗号分隔）
export const likeStatuses = (bizType, bizIds) =>
  request.get(`/likes/statuses/${bizType}`, { params: { bizIds: bizIds.join(',') } })
