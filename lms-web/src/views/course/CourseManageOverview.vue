<script setup>
// 课程管理-概览页（教师，/courses/:id/manage）：课程信息编辑 / 状态与发布（抢课窗口）/ 危险操作（删除课程）
// 布局与页签由 CourseManageLayout 提供；题库、出卷与考试作业、知识库在相邻页签子路由
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourseDetail, updateCourse, changeCourseStatus, publishCourse, deleteCourse, getCourseCatalog, listCategories, addCategory } from '../../api/course'
import { listLessons } from '../../api/learn'
import { uploadMedia } from '../../api/media'
import { queryQuestionsByBiz, queryMyPapers, queryMySchedules } from '../../api/exam'
import { isTeacher } from '../../utils/auth'

const route = useRoute()
const router = useRouter()
const courseId = Number(route.params.id)

const course = ref(null)
const errorMsg = ref('')

// ---------- 全局课程分类标签（下拉选择 + “+” 新增） ----------
const categories = ref([])

const categoryOptions = computed(() => {
  const list = Array.isArray(categories.value) ? [...categories.value] : []
  if (form.category && !list.includes(form.category)) list.unshift(form.category)
  return list
})

const loadCategories = async () => {
  try { categories.value = await listCategories() } catch (e) { categories.value = [] }
}

const onAddCategory = async () => {
  const name = prompt('新分类名称（将全局可见）：')
  if (!name || !name.trim()) return
  try {
    await addCategory(name.trim())
    await loadCategories()
    form.category = name.trim()
    alert('分类「' + name.trim() + '」已添加')
  } catch (e) {
    alert(e.message)
  }
}

// ---------- 课程信息编辑 ----------
const form = reactive({ name: '', category: '', intro: '', cover: '' })
const saving = ref(false)

const loadCourse = async () => {
  try {
    course.value = await getCourseDetail(courseId)
    Object.assign(form, {
      name: course.value.name || '',
      category: course.value.category || '',
      intro: course.value.intro || '',
      cover: course.value.cover || ''
    })
    readiness.cover = !!course.value.cover
    readiness.intro = !!(course.value.intro && course.value.intro.trim())
    readiness.category = !!course.value.category
    const [catR, lsR] = await Promise.allSettled([getCourseCatalog(courseId), listLessons(courseId)])
    const cat = catR.status === 'fulfilled' && Array.isArray(catR.value) ? catR.value : []
    const lessons = lsR.status === 'fulfilled' && Array.isArray(lsR.value) ? lsR.value : []
    readiness.chapters = cat.filter((n) => n.level !== 2).length
    readiness.sections = cat.reduce((s, n) => s + (n.children?.length || 0), 0)
    readiness.lessons = lessons.length
    loadExamReadiness()
  } catch (e) {
    errorMsg.value = e.message
  }
}

const saveCourse = async () => {
  if (!form.name.trim()) { alert('课程名称必填'); return }
  saving.value = true
  try {
    await updateCourse(courseId, { ...form })
    alert('已保存')
    await loadCourse()
  } catch (e) {
    alert(e.message)
  } finally {
    saving.value = false
  }
}

const onCoverFile = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadMedia(file)
    form.cover = res?.url || res?.data?.url || ''
    alert('封面上传成功')
  } catch (err) {
    alert('封面上传失败：' + err.message)
  } finally {
    e.target.value = ''
  }
}

// ---------- 状态与发布 ----------
const statusText = computed(() => {
  const map = { 0: '草稿', 1: '待发布', 2: '抢课中', 3: '进行中', 4: '已结束', 5: '已下架' }
  return map[course.value?.status] || '未知'
})

const showPublishForm = ref(false)
const publishForm = reactive({ grabStart: '', grabEnd: '', stock: '0' })

const readiness = reactive({
  loaded: false, cover: false, intro: false, category: false,
  chapters: 0, sections: 0, lessons: 0, questions: 0, papers: 0, schedules: 0
})

const loadExamReadiness = async () => {
  const settle = async (fn) => { try { return await fn() } catch (e) { return -1 } }
  const [qs, papers, scheds] = await Promise.all([
    settle(() => queryQuestionsByBiz(1, courseId)),
    settle(() => queryMyPapers()),
    settle(() => queryMySchedules())
  ])
  readiness.questions = Array.isArray(qs) ? qs.length : 0
  readiness.papers = Array.isArray(papers) ? papers.filter((p) => Number(p.courseId) === courseId).length : 0
  readiness.schedules = Array.isArray(scheds)
    ? scheds.filter((s) => String(s.courseIds || '').split(',').map((x) => x.trim()).includes(String(courseId))).length
    : 0
  readiness.loaded = true
}

const readinessItems = computed(() => {
  const r = readiness
  const list = [
    { ok: r.cover, label: '封面图' },
    { ok: r.intro, label: '课程简介' },
    { ok: r.category, label: '课程分类' },
    { ok: r.chapters >= 0, label: `章节目录（${r.chapters >= 0 ? r.chapters : '未知'} 章 / ${r.sections} 节）` },
    { ok: r.lessons >= 0, label: `课次（${r.lessons >= 0 ? r.lessons : '未知'}）` }
  ]
  if (r.loaded) {
    list.push({ ok: true, label: `本课程题目 ${r.questions}` })
    list.push({ ok: true, label: `本课程卷面 ${r.papers}` })
    list.push({ ok: true, label: `本课程考试/作业排期 ${r.schedules}` })
  }
  return list
})

const unreadyTip = computed(() => {
  const missing = readinessItems.value.filter((i) => !i.ok).map((i) => i.label)
  return missing.length ? `发布前建议完善：${missing.join('、')}` : ''
})

const togglePublishForm = () => {
  showPublishForm.value = !showPublishForm.value
  if (showPublishForm.value) {
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    if (!publishForm.grabStart) publishForm.grabStart = fmt(now)
    if (!publishForm.grabEnd) publishForm.grabEnd = fmt(new Date(now.getTime() + 24 * 3600 * 1000))
  }
}

const submitPublish = async () => {
  const iso = (v) => (v && v.length === 16 ? v + ':00' : v)
  const start = iso(publishForm.grabStart)
  const end = iso(publishForm.grabEnd)
  if (!start || !end) { alert('请选择抢课开始/结束时间'); return }
  if (start >= end) { alert('抢课开始时间必须早于结束时间'); return }
  const stock = Math.max(0, parseInt(publishForm.stock, 10) || 0)
  try {
    await publishCourse(courseId, { grabStartTime: start, grabEndTime: end, stock })
    alert('已提交发布（待发布）：抢课窗口开放后学生可见，结束后自动转为进行中')
    showPublishForm.value = false
    await loadCourse()
  } catch (e) {
    alert(e.message)
  }
}

const onToggleStatus = async () => {
  try {
    await changeCourseStatus(courseId, course.value.status === 3 ? 5 : 3)
    await loadCourse()
  } catch (e) {
    alert(e.message)
  }
}

// ---------- 删除课程 ----------
const deleting = ref(false)
const onDeleteCourse = async () => {
  if (!confirm(`确定删除课程「${course.value?.name}」？将级联删除选课/目录/章节并清空该课程知识库，不可恢复。`)) return
  deleting.value = true
  try {
    await deleteCourse(courseId)
    alert('课程已删除')
    router.push('/my')
  } catch (e) {
    alert('删除失败：' + e.message)
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  if (!isTeacher()) {
    router.replace(`/courses/${courseId}`)
    return
  }
  loadCategories()
  await loadCourse()
})
</script>

<template>
  <p v-if="errorMsg" class="empty-tip">{{ errorMsg }}</p>
  <template v-else-if="course">
    <div class="panel">
      <h3 class="section-title">① 课程信息</h3>
      <div class="form-row">
        <label>名称</label><input v-model="form.name" />
      </div>
      <div class="form-row">
        <label>分类</label>
        <select v-model="form.category">
          <option value="" disabled>请选择分类</option>
          <option v-for="c in categoryOptions" :key="c" :value="c">{{ c }}</option>
        </select>
        <button v-btn-fx class="btn" title="添加新分类（全局可见）" @click="onAddCategory">＋</button>
      </div>
      <div class="form-row">
        <label>简介</label><textarea v-model="form.intro" rows="2"></textarea>
      </div>
      <div class="form-row">
        <label>封面</label>
        <input v-model="form.cover" placeholder="封面 URL 或上传图片" class="grow" />
        <label class="btn upload-btn">上传图片<input type="file" accept="image/*" hidden @change="onCoverFile" /></label>
      </div>
      <div class="act-row"><button v-btn-fx class="btn btn-primary" :disabled="saving" @click="saveCourse">保存课程信息</button></div>
    </div>

    <div class="panel">
      <div class="sec-head">
        <h3 class="section-title">② 状态与发布</h3>
        <span class="status-badge" :class="course.status === 3 ? 'st-on' : 'st-off'">{{ statusText }}</span>
      </div>
      <template v-if="course.status === 0">
        <div class="ready-grid">
          <span v-for="(it, i) in readinessItems" :key="i" class="ready-item" :class="it.ok ? 'ready-ok' : 'ready-miss'">
            {{ it.ok ? '✓' : '✗' }} {{ it.label }}
          </span>
        </div>
        <p v-if="unreadyTip" class="ready-warn">⚠ {{ unreadyTip }}（仅提示；发布后仍可在课程内继续完善内容与题库/出卷）</p>
        <button v-btn-fx class="btn btn-primary" @click="togglePublishForm">{{ showPublishForm ? '收起发布表单' : '填写发布信息（抢课窗口）' }}</button>
        <template v-if="showPublishForm">
          <div class="form-row"><label>抢课开始</label><input type="datetime-local" v-model="publishForm.grabStart" /></div>
          <div class="form-row"><label>抢课结束</label><input type="datetime-local" v-model="publishForm.grabEnd" /></div>
          <div class="form-row"><label>名额（0=不限）</label><input type="number" min="0" step="1" v-model="publishForm.stock" /></div>
          <div class="act-row">
            <button v-btn-fx class="btn btn-primary" @click="submitPublish">确认发布（提交后进入待发布）</button>
            <button v-btn-fx class="btn" @click="showPublishForm = false">取消</button>
          </div>
        </template>
      </template>
      <template v-else-if="course.status === 1">
        <p class="tip">已提交发布（待发布）。抢课窗口开始后学生端可见；无需操作，也可等待自动流转。</p>
      </template>
      <template v-else>
        <p class="tip" v-if="course.status === 2">抢课中：学生可抢课/选课。</p>
        <p class="tip" v-if="course.status === 3">进行中：学生可学习。可下架停课。</p>
        <p class="tip" v-if="course.status === 5">已下架：学生不可见。可恢复上线。</p>
        <button v-btn-fx v-if="course.status === 3 || course.status === 5" class="btn" @click="onToggleStatus">
          {{ course.status === 3 ? '下架课程' : '恢复上线' }}
        </button>
      </template>
      <p class="tip" style="margin-top: 10px">
        提示：本课程题目 / 卷面 / 考试作业排期 / 知识库请到
        <router-link :to="`/courses/${courseId}/manage/questions`">题库</router-link>、
        <router-link :to="`/courses/${courseId}/manage/papers`">出卷与考试作业</router-link> 与
        <router-link :to="`/courses/${courseId}/manage/kb`">知识库</router-link> 页签维护。
      </p>
    </div>

    <div class="panel danger-panel">
      <h3 class="section-title">③ 危险操作</h3>
      <p class="tip">删除后不可恢复：课程、选课、目录章节将被删除，该课程知识库（向量与文档）会被清空。</p>
      <button v-btn-fx class="btn btn-danger" :disabled="deleting" @click="onDeleteCourse">删除课程</button>
    </div>
  </template>
</template>

<style scoped>
.panel { margin-bottom: 16px; }
.sec-head { display: flex; justify-content: space-between; align-items: center; }
.section-title { margin: 0 0 12px; }
.status-badge { padding: 2px 12px; border-radius: 12px; font-size: 13px; }
.st-on { background: #f0f9eb; color: #67c23a; }
.st-off { background: #fdf6ec; color: #e6a23c; }
.form-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.form-row label { flex: 0 0 90px; color: #555; font-size: 14px; }
.form-row input, .form-row textarea, .form-row select { flex: 1; padding: 6px 10px; border: 1px solid #dcdfe6; border-radius: 4px; }
.form-row .grow { flex: 1; }
.upload-btn { cursor: pointer; white-space: nowrap; }
.act-row { display: flex; gap: 10px; margin-top: 6px; }
.tip { color: #888; font-size: 13px; margin: 4px 0 10px; }
.ready-grid { display: flex; flex-wrap: wrap; gap: 8px; margin: 4px 0 10px; }
.ready-item { font-size: 12px; padding: 2px 10px; border-radius: 12px; background: #f0f2f5; color: #666; }
.ready-ok { background: #f0f9eb; color: #67c23a; }
.ready-miss { background: #fdf6ec; color: #e6a23c; }
.ready-warn { color: #e6a23c; font-size: 13px; }
.danger-panel { border-color: #fbe2e2; }
</style>
