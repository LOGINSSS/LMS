<script setup>
// 教师考试/作业发布台：老师端「考试/作业」模块内容
// 职责：新建考试 / 新建作业（选我的课程 → 选该课程已发布卷面 → 设时间发布）
//      + 我已发布的排期管理（结束/跳课程出卷）
// 出卷（组卷/发布卷面）在课程管理内完成：/courses/:id/manage/papers
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { queryMyCourses } from '../api/course'
import { queryMyPapers, queryMySchedules, publishSchedule, closeSchedule } from '../api/exam'

const router = useRouter()

const courses = ref([])          // 我的课程（发布目标）
const papers = ref([])           // 我的全部卷面
const schedules = ref([])        // 我发布的全部排期
const busy = ref(false)
const msg = ref('')
const flash = (m, err) => {
  msg.value = err ? `【失败】${m}` : m
  setTimeout(() => (msg.value = ''), 6000)
}

// courseId -> 课程名
const courseNameMap = computed(() => {
  const map = {}
  for (const c of courses.value) map[String(c.id)] = c.name
  return map
})

const courseName = (id) => courseNameMap.value[String(id)] || `课程 #${id}`

// ---------- 新建考试 / 新建作业表单 ----------
const showForm = ref(false)
const form = reactive({
  courseId: '',
  paperId: '',
  bizType: 1, // 1 考试 / 2 作业
  title: '',
  startTime: '',
  endTime: '',
  durationMinutes: 90
})

const pad = (n) => String(n).padStart(2, '0')
const fmtLocal = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

const openForm = (bizType) => {
  form.bizType = bizType
  form.paperId = ''
  form.title = ''
  const now = new Date()
  form.startTime = fmtLocal(now)
  const end = new Date(now.getTime() + (bizType === 1 ? 2 : 7) * 24 * 3600 * 1000)
  form.endTime = fmtLocal(end)
  form.durationMinutes = bizType === 1 ? 90 : 60
  showForm.value = true
}

// 当前选中课程下的卷面（发布态可选；草稿提示）
const papersOfCourse = computed(() =>
  papers.value.filter((p) => form.courseId && String(p.courseId) === String(form.courseId))
)
const publishedPapers = computed(() => papersOfCourse.value.filter((p) => p.status === 1))
const draftPaperCount = computed(() => papersOfCourse.value.filter((p) => p.status !== 1).length)

// 选课程后重置卷面
const onCourseChange = () => {
  form.paperId = ''
}

const onCreate = async () => {
  if (!form.courseId) { flash('请选择发布课程', true); return }
  if (!form.paperId) { flash('请选择该课程已发布的卷面', true); return }
  if (!form.title.trim()) { flash('请填写标题', true); return }
  if (!form.startTime || !form.endTime) { flash('请选择开始/截止时间', true); return }
  if (form.startTime >= form.endTime) { flash('截止时间必须晚于开始时间', true); return }
  busy.value = true
  try {
    await publishSchedule({
      title: form.title.trim(),
      bizType: Number(form.bizType),
      paperId: Number(form.paperId),
      courseIds: [Number(form.courseId)],
      startTime: form.startTime,
      endTime: form.endTime,
      durationMinutes: Number(form.durationMinutes) || 90
    })
    flash('发布成功，学生端「考试/作业」可见')
    showForm.value = false
    await loadSchedules()
  } catch (e) {
    flash(e.message, true)
  } finally {
    busy.value = false
  }
}

// ---------- 我的排期管理 ----------
const scheduleRows = computed(() =>
  schedules.value.map((s) => {
    const ids = String(s.courseIds || '').split(',').map((x) => x.trim()).filter(Boolean)
    const owned = ids.filter((id) => courseNameMap.value[id])
    return {
      ...s,
      courseNames: (owned.length ? owned : ids).map(courseName).join('、') || '—',
      firstCourseId: (owned[0] || ids[0] || '')
    }
  })
)

const onCloseSchedule = async (s) => {
  if (!confirm(`结束「${s.title}」？结束后学生不可再作答。`)) return
  try {
    await closeSchedule(s.id)
    flash('排期已结束')
    await loadSchedules()
  } catch (e) {
    flash(e.message, true)
  }
}

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const loadCourses = async () => {
  try {
    const data = await queryMyCourses({ pageNo: 1, pageSize: 500 })
    courses.value = data?.list || []
  } catch (e) {
    flash('课程加载失败：' + e.message, true)
  }
}

const loadPapers = async () => {
  try {
    papers.value = await queryMyPapers()
  } catch (e) {
    flash('卷面加载失败：' + e.message, true)
  }
}

const loadSchedules = async () => {
  try {
    schedules.value = await queryMySchedules()
  } catch (e) {
    flash('排期加载失败：' + e.message, true)
  }
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadPapers(), loadSchedules()])
})
</script>

<template>
  <div class="page">
    <div class="head">
      <h2>考试 / 作业发布台</h2>
      <p class="sub">在某个课程内发布考试或作业：选课程 → 选该课程已发布的卷面 → 设置时间。卷面由「课程管理 → 出卷」组卷发布。</p>
    </div>
    <p v-if="msg" class="msg" :class="{ err: msg.startsWith('【失败】') }">{{ msg }}</p>

    <!-- ① 新建入口 -->
    <section class="panel">
      <div class="create-row">
        <button v-btn-fx class="btn btn-create exam" @click="openForm(1)">＋ 新建考试</button>
        <button v-btn-fx class="btn btn-create hw" @click="openForm(2)">＋ 新建作业</button>
      </div>

      <div v-if="showForm" class="pub-form">
        <div class="form-item">
          <label>发布课程（我的课程）</label>
          <select v-model="form.courseId" @change="onCourseChange">
            <option value="" disabled>请选择课程</option>
            <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <p v-if="!courses.length" class="tip">
            暂无课程，请先到 <router-link to="/courses">课程广场</router-link> 新建课程
          </p>
        </div>

        <template v-if="form.courseId">
          <div class="form-item">
            <label>卷面（该课程已发布）</label>
            <select v-model="form.paperId">
              <option value="" disabled>请选择卷面</option>
              <option v-for="p in publishedPapers" :key="p.id" :value="p.id">
                {{ p.title }}（总分 {{ p.totalScore }}）
              </option>
            </select>
            <p v-if="!publishedPapers.length" class="tip">
              该课程暂无已发布卷面<template v-if="draftPaperCount">（另有 {{ draftPaperCount }} 张草稿）</template>。
              请先到
              <router-link :to="`/courses/${form.courseId}/manage/papers`">本课程出卷页</router-link>
              组卷并发布卷面。
            </p>
          </div>

          <div class="form-item">
            <label>标题</label>
            <input v-model="form.title" :placeholder="`如《${courseName(form.courseId)}》${form.bizType === 1 ? '期中考试' : '单元作业'}`" />
          </div>

          <div class="form-row">
            <div class="form-item">
              <label>类型</label>
              <div class="seg">
                <button type="button" :class="{ active: form.bizType === 1 }" @click="form.bizType = 1">考试</button>
                <button type="button" :class="{ active: form.bizType === 2 }" @click="form.bizType = 2">作业</button>
              </div>
            </div>
            <div class="form-item">
              <label>作答时长（分钟）</label>
              <input v-model.number="form.durationMinutes" type="number" min="1" />
            </div>
          </div>

          <div class="form-row">
            <div class="form-item">
              <label>开始时间</label>
              <input v-model="form.startTime" type="datetime-local" />
            </div>
            <div class="form-item">
              <label>截止时间</label>
              <input v-model="form.endTime" type="datetime-local" />
            </div>
          </div>

          <div class="act-row">
            <button v-btn-fx class="btn btn-primary" :disabled="busy" @click="onCreate">
              {{ busy ? '发布中…' : form.bizType === 1 ? '发布考试' : '发布作业' }}
            </button>
            <button v-btn-fx class="btn" @click="showForm = false">取消</button>
          </div>
        </template>
      </div>
    </section>

    <!-- ② 我发布的排期 -->
    <section class="panel">
      <h3 class="section-title">我已发布的排期</h3>
      <table v-if="scheduleRows.length" class="sched-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>类型</th>
            <th>适用课程</th>
            <th>时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in scheduleRows" :key="s.id">
            <td class="s-title">{{ s.title }}</td>
            <td><span class="chip" :class="s.bizType === 1 ? 'exam' : 'hw'">{{ s.bizType === 1 ? '考试' : '作业' }}</span></td>
            <td>{{ s.courseNames }}</td>
            <td>{{ fmtTime(s.startTime) }} ~ {{ fmtTime(s.endTime) }}</td>
            <td>{{ s.status === 1 ? '发布中' : '已结束' }}</td>
            <td class="ops">
              <button v-btn-fx v-if="s.firstCourseId" class="btn" @click="router.push(`/courses/${s.firstCourseId}/manage/papers`)">出卷管理</button>
              <button v-btn-fx v-if="s.status === 1" class="btn btn-danger" @click="onCloseSchedule(s)">结束</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-tip">还没有发布过考试/作业</p>
    </section>
  </div>
</template>

<style scoped>
.page { padding: 16px; max-width: 1060px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.head h2 { margin-bottom: 4px; }
.head .sub { color: #888; font-size: 13px; }
.msg { color: #2f6fed; }
.msg.err { color: #e05b5b; }
.create-row { display: flex; gap: 12px; }
.btn-create { padding: 10px 22px; color: #fff; border: none; font-size: 15px; }
.btn-create.exam { background: #e05b5b; }
.btn-create.exam:hover { background: #e77c7c; color: #fff; }
.btn-create.hw { background: #2f9e6e; }
.btn-create.hw:hover { background: #55b98d; color: #fff; }
.pub-form { margin-top: 16px; border-top: 1px dashed #e3e6ea; padding-top: 14px; max-width: 640px; }
.form-row { display: flex; gap: 12px; }
.form-row .form-item { flex: 1; }
.seg { display: flex; gap: 0; }
.seg button { flex: 1; padding: 8px 10px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.seg button:first-child { border-radius: 4px 0 0 4px; }
.seg button:last-child { border-radius: 0 4px 4px 0; margin-left: -1px; }
.seg button.active { background: #2f6fed; color: #fff; border-color: #2f6fed; }
.tip { color: #888; font-size: 13px; margin-top: 6px; }
.act-row { display: flex; gap: 10px; }
.section-title { margin: 0 0 10px; }
.sched-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sched-table th, .sched-table td { border: 1px solid #ebeef5; padding: 7px 8px; text-align: left; }
.sched-table th { background: #fafafa; color: #666; }
.s-title { max-width: 240px; }
.chip { padding: 1px 8px; border-radius: 4px; font-size: 12px; color: #fff; }
.chip.exam { background: #e05b5b; }
.chip.hw { background: #2f9e6e; }
.ops { display: flex; gap: 6px; }
</style>
