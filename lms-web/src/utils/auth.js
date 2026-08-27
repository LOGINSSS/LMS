// 登录态管理：token 与用户信息统一存 localStorage（键名 lms_* 前缀）
const TOKEN_KEY = 'lms_token'
const USER_TYPE_KEY = 'lms_user_type'
const USER_ID_KEY = 'lms_user_id'
const USERNAME_KEY = 'lms_username'

export const getToken = () => localStorage.getItem(TOKEN_KEY)

export const getUsername = () => localStorage.getItem(USERNAME_KEY) || ''

export const getUserType = () => Number(localStorage.getItem(USER_TYPE_KEY))

export const isTeacher = () => getUserType() === 2

export const isStudent = () => getUserType() === 1

// 登录成功后写入登录态
export const setAuth = ({ token, userId, userType, username }) => {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_ID_KEY, String(userId ?? ''))
  localStorage.setItem(USER_TYPE_KEY, String(userType ?? ''))
  localStorage.setItem(USERNAME_KEY, username ?? '')
}

// 登出/401 时清空登录态
export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_TYPE_KEY)
  localStorage.removeItem(USER_ID_KEY)
  localStorage.removeItem(USERNAME_KEY)
}
