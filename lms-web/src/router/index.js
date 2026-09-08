import { createRouter, createWebHistory } from 'vue-router'
import { getToken, isTeacher } from '../utils/auth'

// 多地址路由表：public 标记的页面无需登录，其余受登录守卫保护
// 组织原则：
// - 题库 / 出卷不再作为独立板块，迁入课程管理子路由（/courses/:id/manage/*）；
// - 媒资为“给课程/试卷/知识库加文件”的工具能力，不再单列页面；
// - 搜索推荐并入课程广场（关键词 ES 搜索 + 兴趣推荐），不再单列；
// - 日历整合进首页（/calendar 完整版保留，不进导航）；
// - 考试/作业按角色分流：学生待办 / 教师发布台（同一地址不同内容）
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { public: true } },
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/courses', name: 'courses', component: () => import('../views/CourseListView.vue') },
  { path: '/courses/:id', name: 'course-detail', component: () => import('../views/CourseDetailView.vue'), props: true },
  {
    // 课程管理（教师）：概览 / 题库（本课程）/ 出卷与考试作业
    path: '/courses/:id/manage',
    component: () => import('../views/course/CourseManageLayout.vue'),
    meta: { teacherOnly: true },
    children: [
      { path: '', name: 'course-manage', component: () => import('../views/course/CourseManageOverview.vue') },
      { path: 'questions', name: 'course-manage-questions', component: () => import('../views/course/CourseManageQuestions.vue') },
      { path: 'papers', name: 'course-manage-papers', component: () => import('../views/course/CourseManagePapers.vue') },
      { path: 'kb', name: 'course-manage-kb', component: () => import('../views/course/CourseManageKb.vue') }
    ]
  },
  { path: '/my', name: 'my', component: () => import('../views/MyCoursesView.vue') },
  { path: '/exam-schedules', name: 'exam-schedules', component: () => import('../views/ExamScheduleView.vue') },
  { path: '/calendar', name: 'calendar', component: () => import('../views/CalendarView.vue') },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/learn', name: 'learn', component: () => import('../views/LearnView.vue') },
  { path: '/exam-papers/:paperId', name: 'exam-paper', component: () => import('../views/ExamPaperView.vue'), props: true },
  // 未匹配地址兜底回角色主页
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：受保护页未登录 → 跳登录页并带回跳地址；已登录访问登录/注册页 → 回角色主页
// 教师专属路由（课程管理等）学生访问 → 回首页
router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && token) {
    return { path: '/home' }
  }
  if (to.matched.some((r) => r.meta.teacherOnly) && !isTeacher()) {
    return { path: '/home' }
  }
  return true
})

export default router
