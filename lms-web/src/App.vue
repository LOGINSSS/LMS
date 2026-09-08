<script setup>
// 根组件：顶部导航（按角色区分菜单）+ 路由出口 + 全局 AI 聊天浮窗
// 导航原则：媒资/日历为业务能力并入页面内（日历在首页，媒资为上传工具），不再单列；
// 题库/出卷随课程走（我的课程 → 课程管理）；考试/作业按角色分流（学生待办 / 教师发布台）
import { isTeacher } from './utils/auth'
import UserMenu from './components/UserMenu.vue'
import AiChatWidget from './components/AiChatWidget.vue'
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="container topbar-inner">
        <router-link to="/home" class="brand">LMS 在线学习平台</router-link>
        <nav class="nav">
          <router-link to="/home">首页</router-link>
          <router-link to="/courses">课程广场</router-link>
          <router-link to="/my">我的课程</router-link>
          <router-link to="/learn">学习中心</router-link>
          <router-link to="/exam-schedules">{{ isTeacher() ? '考试/作业发布' : '考试/作业' }}</router-link>
          <router-link v-if="isTeacher()" to="/dashboard">数据看板</router-link>
        </nav>
        <div class="user-area">
          <UserMenu />
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
    <!-- 全局 AI 聊天浮窗（0.2 AI 入口） -->
    <AiChatWidget />
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
  white-space: nowrap;
}

.nav {
  display: flex;
  gap: 16px;
  flex: 1;
  flex-wrap: wrap;
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
  white-space: nowrap;
  flex-shrink: 0;
}
</style>
