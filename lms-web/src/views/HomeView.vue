<script setup>
// 首页：登录后的角色入口主页（不再直接进课程列表）
// 人物画像（GET /users/me）+ 角色统计 + 学生学习概览（积分/在学课程/最近学习进度）+ 角色功能入口
// 动效遵循 gsap-skill：只动 transform/opacity，数据到位后入场，卸载前清理
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserMe } from '../api/user'
import { queryEnrolledCourses, queryMyCourses } from '../api/course'
import { myLearnStats, getCourseProgress } from '../api/learn'
import { myInterests } from '../api/search'
import { getDashboard } from '../api/statistics'
import { getUsername, isTeacher, isStudent } from '../utils/auth'
import { useEntrance } from '../composables/useEntrance'

const router = useRouter()

const isTeacherRole = isTeacher()
const isStudentRole = isStudent()

const profile = ref(null)
const loading = ref(true)

// 角色统计：学生看个人学习数据，教师看平台数据；接口失败显示 --，不阻塞页面
const stats = ref([
  { label: '我的课程', value: null },
  { label: isTeacherRole ? '平台用户' : '我的积分', value: null },
  { label: isTeacherRole ? '平台课程' : '兴趣标签', value: null }
])

// 头像兜底：无头像 URL 时取昵称/用户名首字符
const avatarText = computed(() => {
  const name = profile.value?.nickname || getUsername() || ''
  return name.charAt(0) || 'L'
})

// 人物画像扩展信息（教师/学生分开展示）
const personaLines = computed(() => {
  const p = profile.value
  if (!p) return []
  if (isTeacherRole) {
    return [p.college && `院系：${p.college}`, p.title && `职称：${p.title}`, p.bio && `简介：${p.bio}`].filter(Boolean)
  }
  if (isStudentRole) {
    return [
      [p.major, p.grade, p.className].filter(Boolean).join(' · '),
      p.studentNo && `学号：${p.studentNo}`
    ].filter(Boolean)
  }
  return []
})

// 入口卡片配色（循环取色，禁 emoji，用色块区分模块）
const TONES = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9c27b0', '#00bcd4']

// 功能入口：公共入口 + 教师专属入口（题库/看板）
const entranceList = computed(() => {
  const list = [
    { title: '课程广场', desc: '浏览全部课程并选课', to: '/courses' },
    { title: '搜索推荐', desc: '关键词搜索与兴趣推荐', to: '/search' },
    { title: '我的课程', desc: isTeacherRole ? '我创建的课程' : '我选过的课程', to: '/my' },
    { title: '学习中心', desc: '课次学习 / 笔记 / 问答 / 签到 / 积分', to: '/learn' },
    { title: '媒资中心', desc: '文件与视频上传管理', to: '/medias' }
  ]
  if (isTeacherRole) {
    list.push({ title: '题库管理', desc: '题目维护与业务绑定', to: '/admin/questions' })
    list.push({ title: '数据看板', desc: '平台数据总览与排行榜', to: '/dashboard' })
  }
  return list.map((e, i) => ({ ...e, tone: TONES[i % TONES.length] }))
})

// 角色统计加载（教师）：并发请求，单个失败降级为 null
async function loadStats() {
  const settle = async (fn) => {
    try {
      return await fn()
    } catch (e) {
      return null
    }
  }
  const [mine, dash] = await Promise.all([
    settle(() => queryMyCourses({ pageNo: 1, pageSize: 1 })),
    settle(() => getDashboard())
  ])
  stats.value[0].value = mine?.total ?? null
  stats.value[1].value = dash?.userTotal ?? null
  stats.value[2].value = dash?.courseTotal ?? null
}

// 学生学习概览：累计积分 / 我的笔记 / 我的提问 + 最近学习进度（已选课程前 3 门逐门取进度）
const learnOverview = ref({ points: null, noteTotal: null, qaTotal: null, progresses: [] })

// 进度条填充：数据就绪后置 true 触发 scaleX 过渡（只动 transform）
const showProgress = ref(false)

// 学生数据加载：学习统计（lms-learning 聚合接口）+ 在学课程（前 3 门取进度）+ 兴趣标签（search）
async function loadStudentData() {
  const settle = async (fn) => {
    try {
      return await fn()
    } catch (e) {
      return null
    }
  }
  const [myStats, enrolled, tags] = await Promise.all([
    settle(() => myLearnStats()),
    settle(() => queryEnrolledCourses({ pageNo: 1, pageSize: 3 })),
    settle(() => myInterests())
  ])
  // 画像卡统计
  stats.value[0].value = enrolled?.total ?? null
  stats.value[1].value = myStats?.pointsTotal ?? null
  stats.value[2].value = Array.isArray(tags) ? tags.length : null
  // 学习概览
  learnOverview.value.points = myStats?.pointsTotal ?? null
  learnOverview.value.noteTotal = myStats?.noteTotal ?? null
  learnOverview.value.qaTotal = myStats?.qaTotal ?? null
  const list = Array.isArray(enrolled?.list) ? enrolled.list.slice(0, 3) : []
  learnOverview.value.progresses = await Promise.all(
    list.map(async (course) => ({
      course,
      progress: await settle(() => getCourseProgress(course.id))
    }))
  )
}

// 入口卡片批量入场（数据渲染完成后播放）
const entranceGrid = ref(null)
const { play } = useEntrance(entranceGrid)

// 尊重系统「减弱动态效果」设置
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

onMounted(async () => {
  try {
    profile.value = await getUserMe()
  } catch (e) {
    // 画像接口失败不阻塞页面（如旧账号无档案）
  }
  if (isStudentRole) {
    await loadStudentData()
  } else {
    await loadStats()
  }
  loading.value = false
  await nextTick()
  showProgress.value = true
  if (!prefersReducedMotion) play()
})
</script>

<template>
  <div class="home">
    <!-- 人物画像卡片 -->
    <section class="panel profile-card">
      <div class="profile-main">
        <div class="avatar">{{ avatarText }}</div>
        <div class="profile-info">
          <div class="profile-head">
            <h2>{{ profile?.nickname || getUsername() || 'LMS 学员' }}</h2>
            <span class="role-tag">{{ isTeacherRole ? '教师' : '学生' }}</span>
          </div>
          <p v-if="personaLines.length" class="persona-line">{{ personaLines.join(' / ') }}</p>
          <p class="contact-line">
            <span v-if="profile?.phone">{{ profile.phone }}</span>
            <span v-if="profile?.email">{{ profile.email }}</span>
            <span v-if="!profile?.phone && !profile?.email">完善个人资料，让同学更了解你</span>
          </p>
        </div>
        <div class="profile-actions">
          <button v-btn-fx class="btn btn-primary" @click="router.push('/courses')">进入课程广场</button>
          <router-link v-btn-fx class="btn" to="/my">我的课程</router-link>
        </div>
      </div>
      <div class="stats-row">
        <div v-for="s in stats" :key="s.label" class="stat">
          <b>{{ s.value ?? '--' }}</b>
          <span>{{ s.label }}</span>
        </div>
      </div>
    </section>

    <!-- 学生学习概览：积分 + 最近学习进度（仅学生） -->
    <section v-if="isStudentRole" class="panel">
      <h3 class="section-title">学习概览</h3>
      <div class="learn-metrics">
        <div class="learn-metric">
          <b>{{ learnOverview.points ?? '--' }}</b>
          <span>累计积分</span>
        </div>
        <div class="learn-metric">
          <b>{{ learnOverview.noteTotal ?? '--' }}</b>
          <span>我的笔记</span>
        </div>
        <div class="learn-metric">
          <b>{{ learnOverview.qaTotal ?? '--' }}</b>
          <span>我的提问</span>
        </div>
      </div>
      <div v-if="learnOverview.progresses.length" class="progress-list">
        <div v-for="item in learnOverview.progresses" :key="item.course.id" class="progress-item">
          <router-link :to="`/courses/${item.course.id}`" class="progress-name">{{ item.course.name }}</router-link>
          <div class="progress-track">
            <div class="progress-bar" :style="{ transform: showProgress ? `scaleX(${(item.progress ?? 0) / 100})` : 'scaleX(0)' }"></div>
          </div>
          <span class="progress-value">{{ item.progress ?? 0 }}%</span>
        </div>
      </div>
      <p v-else class="empty-tip">还没有学习记录，去课程广场选一门课开始学习吧</p>
    </section>

    <!-- 角色功能入口 -->
    <section class="panel">
      <h3 class="section-title">{{ isTeacherRole ? '教师工作台' : '学习工作台' }}</h3>
      <div ref="entranceGrid" class="entrance-grid">
        <router-link v-for="e in entranceList" :key="e.to" :to="e.to" class="entrance-card">
          <div class="entrance-badge" :style="{ background: e.tone }">{{ e.title.charAt(0) }}</div>
          <div class="entrance-body">
            <div class="entrance-title">{{ e.title }}</div>
            <div class="entrance-desc">{{ e.desc }}</div>
          </div>
          <span class="entrance-arrow">→</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 人物画像卡片 */
.profile-main {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #7d5fff);
  color: #fff;
  font-size: 30px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-head h2 {
  font-size: 22px;
}

.role-tag {
  padding: 2px 10px;
  border-radius: 10px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 13px;
}

.persona-line {
  margin-top: 6px;
  font-size: 14px;
  color: #555;
}

.contact-line {
  margin-top: 4px;
  font-size: 13px;
  color: #999;
  display: flex;
  gap: 14px;
}

.profile-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

/* 统计行 */
.stats-row {
  display: flex;
  margin-top: 18px;
  border-top: 1px solid #f0f2f5;
  padding-top: 14px;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border-right: 1px solid #f0f2f5;
}

.stat:last-child {
  border-right: none;
}

.stat b {
  font-size: 22px;
  color: #409eff;
}

.stat span {
  font-size: 13px;
  color: #888;
}

/* 学习概览 */
.section-title {
  font-size: 16px;
  margin-bottom: 14px;
}

.learn-metrics {
  display: flex;
  margin-bottom: 14px;
}

.learn-metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border-right: 1px solid #f0f2f5;
}

.learn-metric:last-child {
  border-right: none;
}

.learn-metric b {
  font-size: 20px;
  color: #67c23a;
}

.learn-metric span {
  font-size: 13px;
  color: #888;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}

.progress-name {
  flex: 0 0 200px;
  color: #333;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.progress-name:hover {
  color: #409eff;
}

.progress-track {
  flex: 1;
  height: 8px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #67c23a);
  border-radius: 4px;
  transform-origin: left center;
  transition: transform 0.6s ease;
}

.progress-value {
  flex: 0 0 44px;
  text-align: right;
  font-size: 13px;
  color: #555;
}

.empty-tip {
  font-size: 13px;
  color: #999;
}

/* 功能入口 */
.entrance-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.entrance-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  color: #333;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.entrance-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: #409eff;
}

.entrance-badge {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.entrance-body {
  flex: 1;
  min-width: 0;
}

.entrance-title {
  font-size: 15px;
  font-weight: 600;
}

.entrance-desc {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entrance-arrow {
  color: #c0c4cc;
  font-size: 16px;
  flex-shrink: 0;
}
</style>
