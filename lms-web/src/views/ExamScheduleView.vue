<script setup>
// 考试/作业页（角色分流）：
// - 学生：我的考试/作业（日历/列表）：按已报名课程拉取发布物排期，作业/考试分类型查看 → 进入答题页
// - 教师：考试/作业发布台（新建考试/新建作业 + 我的排期管理），见 TeacherScheduleConsole
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listMineSchedules } from '../api/exam'
import { queryEnrolledCourses } from '../api/course'
import { isTeacher } from '../utils/auth'
import TeacherScheduleConsole from '../components/TeacherScheduleConsole.vue'

const isTeacherRole = isTeacher()
const router = useRouter()
const bizType = ref('') // '' 全部 1 考试 2 作业
const list = ref([])
const loading = ref(false)
const errorMsg = ref('')

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const load = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    // 已报名课程 id（无报名则列表为空，页面提示）
    const enrolled = await queryEnrolledCourses({ pageNo: 1, pageSize: 500 })
    const rows = enrolled?.list || enrolled || []
    const ids = rows
      .map((r) => r.courseId ?? r.id ?? r.course?.id)
      .filter((x) => x != null)
    const params = { courseIds: ids.join(',') }
    if (bizType.value) params.bizType = bizType.value
    list.value = await listMineSchedules(params)
  } catch (err) {
    errorMsg.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const goPaper = (s) => {
  router.push({ path: `/exam-papers/${s.paperId}`, query: { scheduleId: s.id, bizType: s.bizType } })
}

const statusText = (s) => {
  const now = Date.now()
  const start = s.startTime ? new Date(s.startTime).getTime() : 0
  const end = s.endTime ? new Date(s.endTime).getTime() : 0
  if (now < start) return '未开始'
  if (now <= end) return '进行中'
  return '已截止'
}

if (!isTeacherRole) onMounted(load)
</script>

<template>
  <!-- 教师：发布台 -->
  <TeacherScheduleConsole v-if="isTeacherRole" />

  <!-- 学生：我的考试/作业 -->
  <div v-else class="page">
    <h2>我的考试 / 作业</h2>
    <div class="tabs">
      <button :class="{ active: bizType === '' }" @click="bizType = ''; load()">全部</button>
      <button :class="{ active: bizType === '1' }" @click="bizType = '1'; load()">考试</button>
      <button :class="{ active: bizType === '2' }" @click="bizType = '2'; load()">作业</button>
      <button style="margin-left:auto" @click="load()">刷新</button>
    </div>

    <p v-if="loading">加载中…</p>
    <p v-else-if="errorMsg" class="err">{{ errorMsg }}</p>
    <p v-else-if="!list.length" class="empty">暂无已发布且未截止的考试/作业（请先报名课程，或等待老师发布）</p>

    <div v-else class="cards">
      <div v-for="s in list" :key="s.id" class="card">
        <div class="row">
          <span class="tag" :class="s.bizType === 1 ? 'exam' : 'hw'">
            {{ s.bizType === 1 ? '考试' : '作业' }}
          </span>
          <strong>{{ s.title }}</strong>
          <span class="status">{{ statusText(s) }}</span>
        </div>
        <div class="meta">开始：{{ fmtTime(s.startTime) }} · 截止：{{ fmtTime(s.endTime) }} · 时长 {{ s.durationMinutes }} 分钟</div>
        <div class="meta">适用课程：{{ s.courseIds }}</div>
        <button class="primary" @click="goPaper(s)">
          {{ s.bizType === 1 ? '进入考试' : '开始作答' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { padding: 16px; max-width: 960px; margin: 0 auto; }
.tabs { display: flex; gap: 8px; margin: 12px 0; }
.tabs button { padding: 6px 14px; border: 1px solid #ccc; background: #fff; cursor: pointer; border-radius: 6px; }
.tabs button.active { background: #2f6fed; color: #fff; border-color: #2f6fed; }
.cards { display: grid; gap: 12px; }
.card { border: 1px solid #e3e6ea; border-radius: 10px; padding: 14px; background: #fff; }
.row { display: flex; align-items: center; gap: 10px; }
.tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; color: #fff; }
.tag.exam { background: #e05b5b; }
.tag.hw { background: #2f9e6e; }
.status { margin-left: auto; color: #888; font-size: 13px; }
.meta { color: #666; font-size: 13px; margin-top: 6px; }
.empty, .err { color: #999; }
.primary { margin-top: 10px; padding: 6px 16px; background: #2f6fed; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
</style>
