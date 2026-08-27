import request from './request'

// 当前登录用户详情（含教师/学生扩展字段，供首页人物画像展示）
export const getUserMe = () => request.get('/users/me')

// 修改当前用户资料（仅更新非 null 字段）
export const updateUserMe = (data) => request.put('/users/me', data)
