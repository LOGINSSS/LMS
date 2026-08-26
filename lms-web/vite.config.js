import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端开发服务器：不再使用 /api 代理转发。
// 原因：Node http-proxy 与网关(WebFlux)在 Connection: close 响应上的解析不兼容
//（"Data after Connection: close"），POST 请求间歇失败。
// 方案：前端经 VITE_API_BASE 直连网关（开发 http://localhost:8080，见 .env.development），
// 跨域由网关 globalcors 配置放行；生产环境改为同域反代。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173
  }
})
