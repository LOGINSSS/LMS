<script setup>
// 课程管理-出卷与考试作业页（教师，/courses/:id/manage/papers）：
// ① 从本课程题库选题 → ② 组卷（卷面 courseId 固定本课程）→ ③ 我的卷面（发布/预览）
// → ④ 发布排期（考试/作业，courseIds=本课程）→ ⑤ 本课程已发布排期管理
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  queryQuestionsByBiz, createPaper, publishPaper, queryMyPapers, getPaperAdmin,
  queryMySchedules, publishSchedule, closeSchedule
} from '../../api/exam'

const route = useRoute()
const courseId = Number(route.params.id)

const questions = ref([])       // 本课程题库
const selected = ref({})        // questionId -> score
const papers = ref([])          // 我的全部卷面
const schedules = ref([])       // 我的全部排期
const preview = ref(null)
const busy = ref(false)
const msg = ref('')
const flash = (m, err) => { msg.value = err ? `【失败】${m}` : m; setTimeout(() => (msg.value = ''), 6000) }

const coursePapers = computed(() => papers.value.filter((p) => String(p.courseId) === String(courseId)))
const courseSchedules = computed(() =>
  schedules.value.filter((s) => String(s.courseIds || '').split(',').map((x) => x.trim()).includes(String(courseId)))
)

// 新建卷面表单
const paperForm = reactive({ title: '', description: '' })
// 发布排期表单
const schedForm = reactive({ title: '', bizType: '1', paperId: '', startTime: '', endTime: '', durationMinutes: 90 })

const pad = (n) => String(n).padStart(2, '0')
const fmtLocal = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

const loadQuestions = async () => {
  try {
    const data = await queryQuestionsByBiz(1, courseId)
    questions.value = Array.isArray(data) ? data : []
  } catch (e) {
    questions.value = []
    flash('题库加载失败：' + e.message, true)
  }
}

const loadAll = async () => {
  const settle = async (fn) => { try { return await fn() } catch (e) { return [] } }
  papers.value = await settle(queryMyPapers)
  schedules.value = await settle(queryMySchedules)
}

const toggleSelect = (q) => {
  if (selected.value[q.id]) delete selected.value[q.id]
  else selected.value[q.id] = 1
}

const selectAll = (checked) => {
  selected.value = {}
  if (checked) questions.value.forEach((q) => { selected.value[q.id] = 1 })
}

const onCreatePaper = async () => {
  if (!paperForm.title.trim() || !Object.keys(selected.value).length) { flash('请填标题并至少选一题', true); return }
  busy.value = true
  try {
    const items = Object.entries(selected.value).map(([id, score]) => ({ id: Number(id), score: Number(score) || 1 }))
    await createPaper({ title: paperForm.title.trim(), description: paperForm.description,
      courseId, items })
    flash('组卷成功（草稿态），请预览后发布卷面')
    selected.value = {}
    paperForm.title = ''; paperForm.description = ''
    await loadAll()
  } catch (e) { flash(e.message, true) } finally { busy.value = false }
}

const onPublishPaper = async (p) => {
  if (!confirm(`发布卷面「${p.title}」？发布后即可在下方排期并推送给学生。`)) return
  try { await publishPaper(p.id); flash('卷面已发布'); await loadAll() }
  catch (e) { flash(e.message, true) }
}

const onPreview = async (p) => {
  try { preview.value = await getPaperAdmin(p.id) } catch (e) { flash(e.message, true) }
}

const resetScheduleForm = () => {
  const now = new Date()
  schedForm.startTime = fmtLocal(now)
  const end = new Date(now.getTime() + (Number(schedForm.bizType) === 1 ? 2 : 7) * 24 * 3600 * 1000)
  schedForm.endTime = fmtLocal(end)
  schedForm.durationMinutes = Number(schedForm.bizType) === 1 ? 90 : 60
}

const onPublishSchedule = async () => {
  if (!schedForm.title.trim() || !schedForm.paperId || !schedForm.startTime || !schedForm.endTime) {
    flash('排期表单不完整', true); return
  }
  if (schedForm.startTime >= schedForm.endTime) { flash('截止时间必须晚于开始时间', true); return }
  busy.value = true
  try {
    await publishSchedule({
      title: schedForm.title.trim(),
      bizType: Number(schedForm.bizType),
      paperId: Number(schedForm.paperId),
      courseIds: [courseId],
      startTime: schedForm.startTime,
      endTime: schedForm.endTime,
      durationMinutes: Number(schedForm.durationMinutes) || 90
    })
    flash('排期已发布，学生端「考试/作业」可见')
    schedForm.title = ''
    await loadAll()
  } catch (e) { flash(e.message, true) } finally { busy.value = false }
}

const onCloseSchedule = async (s) => {
  if (!confirm(`结束「${s.title}」？结束后学生不可再作答。`)) return
  try { await closeSchedule(s.id); flash('排期已结束'); await loadAll() }
  catch (e) { flash(e.message, true) }
}

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const typeLabel = (t) => ({ 1: '单选', 2: '多选', 3: '判断' }[t] || t)

onMounted(async () => {
  await Promise.all([loadQuestions(), loadAll()])
  resetScheduleForm()
})
</script>

<template>
  <div class="page">
    <p v-if="msg" class="msg" :class="{ err: msg.startsWith('【失败】') }">{{ msg }}</p>

    <!-- ① 题库选题 -->
    <div class="panel">
      <div class="sec-head">
        <h3 class="section-title">① 本课程题库选题</h3>
        <div class="sec-ops">
          <label class="pick-all"><input type="checkbox" @change="(e) => selectAll(e.target.checked)" /> 全选</label>
          <router-link class="btn" :to="`/courses/${courseId}/manage/questions`">去题库页维护</router-link>
        </div>
      </div>
      <p v-if="!questions.length" class="tip">
        本课程暂无题目，请先到
        <router-link :to="`/courses/${courseId}/manage/questions`">本课程题库</router-link>
        创建并绑定题目，再回来组卷。
      </p>
      <div v-else class="q-list">
        <div v-for="q in questions" :key="q.id" class="q-row">
          <label class="pick">
            <input type="checkbox" :checked="!!selected[q.id]" @change="toggleSelect(q)" />
            [{{ typeLabel(q.type) }}] {{ q.name }}
          </label>
          <input v-if="selected[q.id]" v-model.number="selected[q.id]" type="number" min="1" placeholder="分值" class="score" />
        </div>
        <p class="picked">已选 {{ Object.keys(selected).length }} 题</p>
      </div>
    </div>

    <!-- ② 新建卷面 -->
    <div class="panel">
      <h3 class="section-title">② 新建卷面（组卷草稿）</h3>
      <div class="form-grid">
        <input v-model="paperForm.title" placeholder="卷面标题（如《数据结构》单元卷）" />
        <textarea v-model="paperForm.description" placeholder="卷面说明（可选）" rows="2"></textarea>
        <button class="btn btn-primary" :disabled="busy || !questions.length" @click="onCreatePaper">组卷（草稿）</button>
      </div>
    </div>

    <!-- ③ 我的卷面（本课程） -->
    <div class="panel">
      <h3 class="section-title">③ 我的卷面（本课程 {{ coursePapers.length }}）</h3>
      <table v-if="coursePapers.length" class="tbl">
        <thead><tr><th>标题</th><th>状态</th><th>总分</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="p in coursePapers" :key="p.id">
            <td class="c-title">{{ p.title }}</td>
            <td>{{ p.status === 1 ? '已发布' : '草稿' }}</td>
            <td>{{ p.totalScore }}</td>
            <td class="ops">
              <button v-btn-fx class="btn" @click="onPreview(p)">预览</button>
              <button v-btn-fx v-if="p.status !== 1" class="btn btn-primary" @click="onPublishPaper(p)">发布卷面</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="tip">暂无本课程卷面，先在上方组卷。</p>

      <div v-if="preview" class="preview">
        <h4>预览：{{ preview.title }}（总分 {{ preview.totalScore }}，含答案，仅供老师）</h4>
        <ol>
          <li v-for="it in preview.items" :key="it.seq">
            [{{ typeLabel(it.type) }}] {{ it.stem }}
            <span class="ans">答案：{{ it.answer }}（{{ it.score }} 分）</span>
            <div v-if="it.analysis" class="analysis">{{ it.analysis }}</div>
          </li>
        </ol>
        <button v-btn-fx class="btn" @click="preview = null">关闭预览</button>
      </div>
    </div>

    <!-- ④ 发布排期（考试/作业） -->
    <div class="panel">
      <h3 class="section-title">④ 发布考试 / 作业排期（适用课程固定为本课程）</h3>
      <div class="form-grid sched">
        <div class="seg">
          <button type="button" :class="{ active: schedForm.bizType === '1' }" @click="schedForm.bizType = '1'; resetScheduleForm()">考试</button>
          <button type="button" :class="{ active: schedForm.bizType === '2' }" @click="schedForm.bizType = '2'; resetScheduleForm()">作业</button>
        </div>
        <input v-model="schedForm.title" placeholder="标题（如《数据结构》期中考试）" />
        <select v-model="schedForm.paperId">
          <option value="" disabled>选择本课程已发布卷面…</option>
          <option v-for="p in coursePapers.filter((x) => x.status === 1)" :key="p.id" :value="p.id">{{ p.title }}</option>
        </select>
        <label class="dl">开始 <input v-model="schedForm.startTime" type="datetime-local" /></label>
        <label class="dl">截止 <input v-model="schedForm.endTime" type="datetime-local" /></label>
        <input v-model.number="schedForm.durationMinutes" type="number" placeholder="时长（分钟）" />
        <button class="btn btn-primary" :disabled="busy" @click="onPublishSchedule">发布排期</button>
      </div>
      <p v-if="!coursePapers.some((x) => x.status === 1)" class="tip">
        需要先在上方把卷面「发布」后才能挂排期。
      </p>
    </div>

    <!-- ⑤ 本课程已发布排期 -->
    <div class="panel">
      <h3 class="section-title">⑤ 本课程考试 / 作业排期（{{ courseSchedules.length }}）</h3>
      <table v-if="courseSchedules.length" class="tbl">
        <thead><tr><th>标题</th><th>类型</th><th>卷面</th><th>时间</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="s in courseSchedules" :key="s.id">
            <td class="c-title">{{ s.title }}</td>
            <td>{{ s.bizType === 1 ? '考试' : '作业' }}</td>
            <td>{{ s.paperId }}</td>
            <td>{{ fmtTime(s.startTime) }} ~ {{ fmtTime(s.endTime) }}</td>
            <td>{{ s.status === 1 ? '发布中' : '已结束' }}</td>
            <td><button v-btn-fx v-if="s.status === 1" class="btn btn-danger" @click="onCloseSchedule(s)">结束</button></td>
          </tr>
        </tbody>
      </table>
      <p v-else class="tip">本课程暂无排期。发布后，学生端「考试/作业」与日历可见。</p>
    </div>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.panel { margin-bottom: 0; }
.sec-head { display: flex; justify-content: space-between; align-items: center; }
.sec-head .section-title { margin: 0; }
.sec-ops { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.pick-all { color: #666; display: flex; align-items: center; gap: 4px; cursor: pointer; }
.section-title { margin: 0 0 10px; }
.q-list { max-height: 300px; overflow: auto; }
.q-row { display: flex; gap: 8px; align-items: center; padding: 4px 0; border-bottom: 1px dashed #eee; }
.pick { flex: 1; cursor: pointer; font-size: 14px; }
.score { width: 80px; padding: 3px 6px; border: 1px solid #ccc; border-radius: 4px; }
.picked { color: #2f6fed; font-size: 13px; margin-top: 6px; }
.form-grid { display: flex; flex-direction: column; gap: 8px; }
.form-grid input, .form-grid select, .form-grid textarea { padding: 6px 10px; border: 1px solid #ccc; border-radius: 6px; font-size: 14px; }
.form-grid.sched { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.form-grid.sched .seg { display: flex; }
.form-grid.sched .seg button { flex: 1; padding: 8px 10px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.form-grid.sched .seg button:first-child { border-radius: 6px 0 0 6px; }
.form-grid.sched .seg button:last-child { border-radius: 0 6px 6px 0; margin-left: -1px; }
.form-grid.sched .seg button.active { background: #2f6fed; color: #fff; border-color: #2f6fed; }
.form-grid.sched .dl { display: flex; align-items: center; gap: 6px; color: #666; font-size: 13px; }
.form-grid.sched .dl input { flex: 1; }
.form-grid.sched button.btn-primary { grid-column: 1 / -1; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; }
.tbl th, .tbl td { border: 1px solid #ebeef5; padding: 6px 8px; text-align: left; }
.tbl th { background: #fafafa; color: #666; }
.c-title { max-width: 260px; }
.ops { display: flex; gap: 6px; }
.tip { color: #888; font-size: 13px; }
.msg { color: #2f6fed; }
.msg.err { color: #e05b5b; }
.preview { border: 1px solid #2f6fed; border-radius: 8px; padding: 12px; margin-top: 10px; background: #f6f9ff; max-height: 340px; overflow: auto; }
.preview h4 { margin: 0 0 8px; }
.preview ol { padding-left: 20px; font-size: 14px; }
.ans { color: #2f9e6e; font-size: 12px; margin-left: 8px; }
.analysis { color: #888; font-size: 12px; }
</style>
