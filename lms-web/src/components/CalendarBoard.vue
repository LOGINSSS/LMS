<script setup>
// 日历面板（可复用组件）：月视图多色事件 + 周课表，支持翻页/今天/月周切换
// 角色区分课程集：学生 → 已选课程；教师 → 自己创建的课程（排课/考试/作业都可看）
// 事件点击：学生答题/课程照旧；教师点考试/作业 → 发布台，点上课 → 课程管理
// embedded 模式用于首页内嵌（更紧凑，不带头部标题）
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { mineCalendar } from '../api/calendar'
import { queryEnrolledCourses, queryMyCourses } from '../api/course'
import { isTeacher, isStudent } from '../utils/auth'

const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const router = useRouter()
const mode = ref('month') // month | week
const cursor = ref(new Date())
const events = ref([])
const loading = ref(false)
const errMsg = ref('')
let courseIds = []

const fmt = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const monthRange = () => {
  const first = new Date(cursor.value.getFullYear(), cursor.value.getMonth(), 1)
  const last = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + 1, 0)
  return [first, last]
}

const weekRange = () => {
  const d = new Date(cursor.value)
  const dow = (d.getDay() + 6) % 7 // 周一=0
  const mon = new Date(d.getFullYear(), d.getMonth(), d.getDate() - dow)
  const sun = new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + 6)
  return [mon, sun]
}

const visibleRange = () => (mode.value === 'week' ? weekRange() : monthRange())

const load = async () => {
  loading.value = true
  errMsg.value = ''
  try {
    const [s, e] = visibleRange()
    events.value = await mineCalendar({ courseIds: courseIds.join(','), start: fmt(s), end: fmt(e) })
  } catch (err) {
    errMsg.value = err.message || '日历加载失败'
  } finally {
    loading.value = false
  }
}

// 月份网格（周一起始）
const grid = computed(() => {
  const [s] = monthRange()
  const startWeekday = (s.getDay() + 6) % 7
  const cells = []
  const lead = new Date(s.getFullYear(), s.getMonth(), s.getDate() - startWeekday)
  for (let i = 0; i < 42; i++) {
    const d = new Date(lead.getFullYear(), lead.getMonth(), lead.getDate() + i)
    cells.push({ date: d, inMonth: d.getMonth() === s.getMonth(), dayEvents: eventsBy(fmt(d)) })
  }
  return cells
})

const eventsBy = (dateStr) => events.value.filter((e) => String(e.start).slice(0, 10) === dateStr)

const weekDays = computed(() => {
  const [s] = weekRange()
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(s.getFullYear(), s.getMonth(), s.getDate() + i)
    return { date: d, dayEvents: eventsBy(fmt(d)) }
  })
})

const typeLabel = (t) => ({ class: '上课', exam: '考试', assignment: '作业', activity: '活动' }[t] || t)

const timeOf = (iso) => (iso ? String(iso).slice(11, 16) : '')

// 事件跳转（按角色分流：教师不进入学生答题页）
const goEvent = (e) => {
  const p = e.jump?.path
  if (isTeacher()) {
    if (e.type === 'class' && e.courseId) {
      router.push(`/courses/${e.courseId}/manage`)
    } else if (e.type === 'exam' || e.type === 'assignment') {
      router.push('/exam-schedules')
    } else if (p) {
      router.push(p)
    }
    return
  }
  if (p) router.push(p)
  else if (e.type === 'class' && e.courseId) router.push(`/courses/${e.courseId}`)
}

const move = (dir) => {
  const n = new Date(cursor.value)
  if (mode.value === 'week') n.setDate(n.getDate() + dir * 7)
  else n.setMonth(n.getMonth() + dir)
  cursor.value = n
  load()
}

const switchMode = (m) => {
  mode.value = m
  cursor.value = new Date()
  load()
}

const titleText = computed(() => {
  if (mode.value === 'month') return `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`
  const [s, e] = weekRange()
  return `${fmt(s)} ~ ${fmt(e)}`
})

const maxChips = computed(() => (props.embedded ? 2 : 3))

onMounted(async () => {
  try {
    // 学生：已选课程；教师：自己创建的课程（否则教师日历永远为空）
    const api = isTeacher() ? queryMyCourses : queryEnrolledCourses
    const enrolled = await api({ pageNo: 1, pageSize: 500 })
    const rows = enrolled?.list || enrolled || []
    courseIds = rows.map((r) => r.courseId ?? r.id ?? r.course?.id).filter((x) => x != null)
  } catch (e) {
    errMsg.value = e.message || '课程加载失败'
  }
  await load()
})
</script>

<template>
  <div class="calendar-board" :class="{ embedded }">
    <div class="ctrl">
      <button class="btn" @click="move(-1)">‹ 上一{{ mode === 'week' ? '周' : '月' }}</button>
      <strong class="title">{{ titleText }}</strong>
      <button class="btn" @click="move(1)">下一{{ mode === 'week' ? '周' : '月' }} ›</button>
      <button class="btn" @click="cursor = new Date(); load()">今天</button>
      <span class="sep"></span>
      <button :class="{ active: mode === 'month' }" @click="switchMode('month')">月</button>
      <button :class="{ active: mode === 'week' }" @click="switchMode('week')">周课表</button>
    </div>
    <p v-if="loading" class="tip">加载中…</p>
    <p v-else-if="errMsg" class="tip err">{{ errMsg }}</p>

    <!-- 月视图 -->
    <div v-else-if="mode === 'month'" class="month">
      <div class="week-row">
        <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w" class="week-cell">{{ w }}</span>
      </div>
      <div class="grid">
        <div v-for="(cell, i) in grid" :key="i" class="day" :class="{ dim: !cell.inMonth }">
          <div class="day-num">{{ cell.date.getDate() }}</div>
          <div class="chip" v-for="(e, j) in cell.dayEvents.slice(0, maxChips)" :key="j"
               :style="{ background: e.color }" :title="e.title" @click="goEvent(e)">
            {{ timeOf(e.start) }} {{ e.title }}
          </div>
          <div v-if="cell.dayEvents.length > maxChips" class="more">+{{ cell.dayEvents.length - maxChips }}</div>
        </div>
      </div>
    </div>

    <!-- 周课表 -->
    <div v-else class="week">
      <div class="week-row">
        <span v-for="d in weekDays" :key="d.date" class="week-cell">
          {{ ['一', '二', '三', '四', '五', '六', '日'][(d.date.getDay() + 6) % 7] }}
          {{ d.date.getMonth() + 1 }}/{{ d.date.getDate() }}
        </span>
      </div>
      <div class="week-grid">
        <div v-for="d in weekDays" :key="d.date" class="day">
          <div v-if="!d.dayEvents.length" class="empty">—</div>
          <div class="chip" v-for="(e, j) in d.dayEvents" :key="j"
               :style="{ background: e.color }" @click="goEvent(e)">
            {{ timeOf(e.start) }}-{{ timeOf(e.end) }} {{ typeLabel(e.type) }} {{ e.title }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ctrl { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.btn { padding: 4px 10px; border: 1px solid #ccc; background: #fff; border-radius: 6px; cursor: pointer; font-size: 13px; }
.btn.active { background: #2f6fed; color: #fff; border-color: #2f6fed; }
.title { font-size: 14px; min-width: 130px; text-align: center; }
.sep { width: 1px; height: 20px; background: #ddd; }
.week-row { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; color: #888; font-size: 12px; }
.grid, .week-grid { display: grid; grid-template-columns: repeat(7, 1fr); border: 1px solid #e3e6ea; border-radius: 8px; overflow: hidden; }
.day { min-height: 96px; border: 1px solid #f0f2f5; padding: 4px; background: #fff; }
.day.dim { background: #fafbfc; color: #bbb; }
.day-num { font-size: 12px; color: #666; }
.chip { margin: 2px 0; padding: 1px 5px; border-radius: 4px; color: #fff; font-size: 11px; cursor: pointer; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.more { font-size: 11px; color: #999; }
.empty { color: #ddd; text-align: center; padding-top: 20px; }
.tip { color: #999; font-size: 13px; padding: 12px 0; }
.tip.err { color: #e05b5b; }

/* 首页内嵌更紧凑 */
.embedded .day { min-height: 72px; }
.embedded .ctrl { gap: 6px; }
.embedded .btn { padding: 3px 8px; font-size: 12px; }
.embedded .title { font-size: 13px; min-width: 110px; }
.embedded .day-num { font-size: 11px; }
.embedded .chip { font-size: 10px; }
</style>
