# lms-web 前端（Vue3 + Vite）

> LMS 前端单页应用：登录 / 注册、课程列表与详情、我的课程、媒资上传、搜索推荐、数据看板、学习中心、题库管理。

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
| CourseListView | 课程卡片分页（CourseCard 组件 + Pagination 分页组件） |
| CourseDetailView | 课程详情（点赞 / 课次 / 笔记 / 问答） |
| MyCoursesView | 我的课程（选课 / 退课） |
| MediaView | 媒资上传与我的媒资管理 |
| SearchView | ES 课程搜索 / 兴趣推荐 |
| DashboardView | 数据看板（总览 / 今日 / Top10） |
| LearnView | 学习中心（课次 / 进度 / 笔记 / 签到 / 积分） |
| QuestionManageView | 题库管理（建题 / 编辑 / 删除 / 分页） |

## 代码分层（src）

| 目录 | 说明 |
|---|---|
| `api/` | 按域拆分的接口封装：auth / course / exam / learn / like / media / search / statistics + `request.js`（axios 实例：baseURL、token 注入、401 跳登录） |
| `router/index.js` | 路由表 + 登录守卫（未登录跳 /login） |
| `utils/auth.js` | token 存取 / 用户信息（localStorage） |
| `components/` | CourseCard（课程卡片）、Pagination（分页） |
| `composables/useEntrance.js` | 卡片 stagger 入场动效 |
| `directives/btn-fx.js` | 按钮 hover / 按压回弹动效指令 |
| `styles/main.css` | 全局样式 |
| `views/` | 页面组件（见上表） |

## 启动

```bash
cd lms-web
npm install
npm run dev      # http://localhost:5173
```
