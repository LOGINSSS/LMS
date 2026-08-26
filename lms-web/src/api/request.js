import axios from 'axios'
import router from '../router'
import { getToken, clearAuth } from '../utils/auth'

// axios 实例：baseURL 走环境变量（开发直连网关 8080，生产同域反代），见 .env.development
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000
})

// 请求拦截：自动携带登录 token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包统一响应 R（code=1 成功），业务错误抛 Error(msg)；HTTP 401 清登录态并跳登录
request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code === 1) {
      return body.data
    }
    const err = new Error(body?.msg || '请求失败')
    err.code = body?.code
    throw err
  },
  (error) => {
    if (error.response?.status === 401) {
      clearAuth()
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    const msg = error.response?.data?.msg || error.message || '网络异常'
    throw new Error(msg)
  }
)

export default request
