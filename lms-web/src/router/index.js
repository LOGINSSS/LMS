import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../utils/auth'

// 多地址路由表：public 标记的页面无需登录，其余受登录守卫保护
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { public: true } },
  { path: '/', redirect: '/courses' },
  { path: '/courses', name: 'courses', component: () => import('../views/CourseListView.vue') },
  { path: '/courses/:id', name: 'course-detail', component: () => import('../views/CourseDetailView.vue'), props: true },
  { path: '/my', name: 'my', component: () => import('../views/MyCoursesView.vue') },
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue') },
  { path: '/medias', name: 'medias', component: () => import('../views/MediaView.vue') },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/learn', name: 'learn', component: () => import('../views/LearnView.vue') },
  { path: '/admin/questions', name: 'admin-questions', component: () => import('../views/QuestionManageView.vue') },
  // 未匹配地址兜底回课程列表
  { path: '/:pathMatch(.*)*', redirect: '/courses' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：受保护页未登录 → 跳登录页并带回跳地址；已登录访问登录/注册页 → 回课程列表
router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && token) {
    return { path: '/courses' }
  }
  return true
})

export default router
