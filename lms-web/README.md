# lms-web 前端（Vue3 + Vite）

> LMS 前端单页应用。信息架构按角色组织：**学生**（首页/课程广场/我的课程/学习中心/考试作业）与**教师**（+ 考试作业发布台、数据看板），题库 · 出卷 · 知识库全部按课程放在「课程管理」内，不再单列板块；搜索推荐并入课程广场，日历展示在首页。

## 运行信息

| 项 | 值 |
|---|---|
| dev server | http://localhost:5173（vite.config.js port 5173） |
| 接口入口 | 直连网关 http://localhost:8080（跨域由网关 globalcors 放行，未用 vite proxy） |
| 技术栈 | Vue 3.5 + Vite 5 + vue-router 4 + axios + GSAP（动效） |

## 页面路由（src/views）

| 视图 | 说明 |
|---|---|
| LoginView / RegisterView | 登录 / 注册（用户类型：学生 / 教师） |
| HomeView | 首页：个人画像卡 → **日历/课表**（CalendarBoard）→ 学习概览（学生）/ 功能入口；资料完善引导至右上角昵称下拉 |
| CourseListView | **课程广场**：浏览 + 关键词（ES）搜索 + 兴趣推荐（点击积累兴趣标签），教师可建课、学生可选课 |
| CourseDetailView | 课程详情（点赞 / 大纲正文 / 课次 / 积分榜 / 课程问答入口）。**学生**：学习笔记；**教师**：大纲编辑 |
| CourseQaView | 课程问答子页面 `/courses/:id/qa`：历史问题与回答、状态筛选；学生发布问题，教师在问题线程内回答；通知按 `questionId` 精确定位 |
| MyCoursesView | 我的课程：学生看选过的课，教师看自己创建的课 |
| LearnView | 学习中心：课次 / 进度 / 签到 / 积分明细与榜单 |
| ExamScheduleView | **考试/作业（角色分流）**：学生 = 我的待做考试/作业列表；教师 = 发布台（新建考试/作业 + 我的排期管理，见 TeacherScheduleConsole） |
| CalendarView | 完整日历 / 课表页（复用 CalendarBoard；首页已内嵌，此页不进导航） |
| ExamPaperView | 卷面答题：作业即交即判可重做 / 考试锁页防作弊异步交卷 |
| DashboardView | 数据看板（教师）：总览 / 今日 / Top10 |
| course/CourseManageLayout + 子页 | **课程管理（教师）**：页签 = 概览（信息/状态发布/危险操作）｜题库（本课程）｜出卷与考试作业｜知识库 |

## 关键组件（src/components）

| 组件 | 说明 |
|---|---|
| CourseCard / Pagination | 课程卡片 / 通用分页 |
| CalendarBoard | 日历面板（月/周，角色区分课程源：学生=已选课程、教师=自建课程），首页内嵌与 /calendar 复用 |
| UserMenu | 右上角昵称下拉：完善个人资料（角色字段弹窗）、站内消息（未读状态、跳转到具体课程问题）、退出登录 |
| qa/QuestionThread | 单个问题与历史回答线程，提供教师页面内回答编辑器 |
| TeacherScheduleConsole | 教师「考试/作业」发布台：新建考试/新建作业（选课程 → 选已发布卷面 → 设时间）+ 排期管理 |
| AiChatWidget | 右下角全局 AI 对话浮窗（student / teacher 双角色） |

## 代码分层（src）

| 目录 | 说明 |
|---|---|
| `api/` | 按域拆分的接口封装：auth / course / exam / learn / like / media / search / statistics / calendar / rag / agent + `request.js`（axios 实例：baseURL、token 注入、401 跳登录） |
| `router/index.js` | 路由表 + 守卫（未登录跳 /login；课程管理教师专属）；课程管理子路由 `/courses/:id/manage(/questions|/papers|/kb)` |
| `utils/auth.js` | token / 用户信息存取（localStorage），含昵称同步 |
| `components/` | 见上表 |
| `composables/useEntrance.js` | 卡片 stagger 入场动效 |
| `directives/btn-fx.js` | 按钮 hover / 按压回弹动效指令 |
| `styles/main.css` | 全局样式 |
| `views/` | 页面组件（见路由表） |

## 启动

```bash
cd lms-web
npm install
npm test         # Node 内置测试（无需额外测试依赖）
npm run dev      # http://localhost:5173
```
