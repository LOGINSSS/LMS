<script setup>
// 根组件：顶部导航（课程列表 / 我的课程 / 退出登录）+ 路由出口
import { useRouter } from 'vue-router'
import { isTeacher, isStudent, clearAuth } from './utils/auth'
import { logout } from './api/auth'

const router = useRouter()

// 当前用户身份标签：教师 / 学生
const roleLabel = () => {
  if (isTeacher()) return '教师'
  if (isStudent()) return '学生'
  return ''
}

// 退出登录：调后端把 token 加入黑名单，本地清登录态后回登录页
const handleLogout = async () => {
  try {
    await logout()
  } catch (e) {
    // 登出接口失败不阻塞本地登出
  }
  clearAuth()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="container topbar-inner">
        <router-link to="/courses" class="brand">LMS 在线学习平台</router-link>
        <nav class="nav">
          <router-link to="/courses">课程列表</router-link>
          <router-link to="/my">我的课程</router-link>
        </nav>
        <div class="user-area">
          <span class="role-tag">{{ roleLabel() }}</span>
          <button v-btn-fx class="btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </header>
    <main class="container">
      <!-- 页面切换过渡：淡入 + 轻微上移（CSS 实现，保持轻量） -->
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
/* 页面切换过渡：只动 opacity/transform */
.page-enter-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-active {
  transition: opacity 0.15s ease;
}

.page-leave-to {
  opacity: 0;
}

.topbar {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.topbar-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 56px;
}

.brand {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}

.nav a {
  color: #333;
  font-size: 15px;
}

.nav a.router-link-active {
  color: #409eff;
  font-weight: 600;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag {
  padding: 2px 10px;
  border-radius: 10px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 13px;
}
</style>
