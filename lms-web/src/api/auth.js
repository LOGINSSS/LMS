import request from './request'

// 账号密码登录：返回解包后的 { token, userId, userType, username }
export const login = (data) => request.post('/auth/login', data)

// 注册（学生/教师）：userType 1 学生 / 2 教师
export const register = (data) => request.post('/auth/register', data)

// 登出：token 加入黑名单
export const logout = () => request.post('/auth/logout')
