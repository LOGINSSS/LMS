<script setup>
// 卷面答题：作业（即交即判可重做）/ 考试（锁页防作弊，离页警告累计超时自动交卷走 Kafka 异步）
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getPaper, submitSchedule, submitScheduleAsync } from '../api/exam'

const route = useRoute()
const paperId = Number(route.params.paperId)
const scheduleId = Number(route.query.scheduleId)
const isExam = String(route.query.bizType) === '1'

const paper = ref(null)
const answers = reactive({}) // questionId -> userAnswer
const submitting = ref(false)
const result = ref(null)
const errorMsg = ref('')

// ---- 考试锁页：离页/切后台警告，累计离场超时自动交卷判 0 分由服务端窗口控制；本地超时自动交当前作答 ----
const awaySeconds = ref(0)
const awayTimer = ref(null)
const warnedCount = ref(0)
let lastAwayAt = null

const typeText = (t) => ({ 1: '单选', 2: '多选', 3: '判断' }[t] || '未知')

const buildAnswers = () =>
  Object.entries(answers).filter(([, v]) => String(v ?? '').trim() !== '').map(([k, v]) => ({ questionId: Number(k), userAnswer: String(v).trim() }))

const onLeave = () => {
  if (!isExam || !paper.value || result.value) return
  warnedCount.value += 1
  if (warnedCount.value >= 3) {
    autoSubmit()
    return
  }
  alert(`离开考试页面将被警告（${warnedCount.value}/3）。连续离开或离开累计超 30 秒将自动交卷。`)
  lastAwayAt = Date.now()
  if (!awayTimer.value) {
    awayTimer.value = setInterval(() => {
      if (lastAwayAt) {
        awaySeconds.value = Math.round((Date.now() - lastAwayAt) / 1000)
        if (awaySeconds.value >= 30) autoSubmit()
      }
    }, 1000)
  }
}

const onBack = () => {
  if (!isExam) return
  lastAwayAt = null
}

const autoSubmit = async () => {
  if (result.value || submitting.value) return
  try {
    await doSubmit()
    alert('离开时间过长，已自动交卷')
  } catch (err) {
    alert('自动交卷失败：' + err.message)
  }
}

const doSubmit = async () => {
  submitting.value = true
  errorMsg.value = ''
  try {
    const payload = { answers: buildAnswers() }
    result.value = isExam
      ? await submitScheduleAsync(scheduleId, payload)
      : await submitSchedule(scheduleId, payload)
  } catch (err) {
    errorMsg.value = err.message || '提交失败'
  } finally {
    submitting.value = false
  }
}

const onRetry = () => {
  result.value = null
  Object.keys(answers).forEach((k) => delete answers[k])
}

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

onMounted(async () => {
  try {
    paper.value = await getPaper(paperId)
    ;(paper.value.items || []).forEach((q) => { answers[q.questionId] = '' })
    if (isExam) {
      const warn = () => onLeave()
      const back = () => onBack()
      window.addEventListener('blur', warn)
      window.addEventListener('beforeunload', warn)
      document.addEventListener('visibilitychange', () => (document.hidden ? warn() : back()))
      // 简单全屏锁（用户手势后生效；应用在 iframe/新窗口时浏览器会拦截，属里程碑项）
      if (document.documentElement.requestFullscreen) {
        document.documentElement.requestFullscreen().catch(() => {})
      }
    }
  } catch (err) {
    errorMsg.value = err.message || '卷面加载失败'
  }
})

onBeforeUnmount(() => {
  if (awayTimer.value) clearInterval(awayTimer.value)
  window.removeEventListener('blur', onLeave)
  window.removeEventListener('beforeunload', onLeave)
})
</script>

<template>
  <div class="page" :class="{ exam: isExam }">
    <template v-if="errorMsg && !paper">
      <p class="err">{{ errorMsg }}</p>
    </template>

    <template v-else-if="paper">
      <div class="head">
        <h2>{{ paper.title }}</h2>
        <div class="meta">
          共 {{ paper.items?.length }} 题 · 总分 {{ paper.totalScore }}
          <span v-if="isExam" class="warn">考试模式：请勿离开页面（警告 3 次或离开累计 30 秒将自动交卷）离场 {{ awaySeconds }}s</span>
        </div>
      </div>

      <div v-if="result" class="result">
        <h3>判分结果</h3>
        <p v-if="isExam">已异步提交（submissionId: {{ result.submissionId }}），服务端批改后可在记录中查看。</p>
        <template v-else>
          <p><strong>得分 {{ result.score }} / {{ result.totalScore }}</strong> · 答对 {{ result.correctCount }} / {{ result.questionCount }}</p>
          <ul class="details">
            <li v-for="d in result.details" :key="d.questionId">
              第 {{ d.seq }} 题：{{ d.correct === 1 ? '✓' : '✗' }}（{{ d.score }} 分）
            </li>
          </ul>
          <button class="primary" @click="onRetry">重新作答（作业可重做）</button>
        </template>
      </div>

      <div v-else class="questions">
        <div v-for="q in paper.items" :key="q.questionId" class="q">
          <div class="q-head"><span class="tag">{{ typeText(q.type) }}</span> {{ q.seq }}. {{ q.stem }}</div>
          <div v-if="q.type === 3" class="q-ans">
            <select v-model="answers[q.questionId]">
              <option value="">请选择</option>
              <option value="true">正确</option>
              <option value="false">错误</option>
            </select>
          </div>
          <div v-else class="q-ans">
            <input v-model="answers[q.questionId]" :placeholder="q.type === 2 ? '多选用逗号分隔，如 A,B' : '填入选项，如 A'" />
          </div>
        </div>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
        <button class="primary" :disabled="submitting" @click="doSubmit">
          {{ submitting ? '提交中…' : isExam ? '交卷' : '提交作业' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page { padding: 16px; max-width: 760px; margin: 0 auto; }
.head h2 { margin: 0 0 6px; }
.meta { color: #666; font-size: 13px; }
.warn { color: #e05b5b; margin-left: 12px; }
.q { border: 1px solid #e3e6ea; border-radius: 8px; padding: 12px; margin: 10px 0; background: #fff; }
.q-head { margin-bottom: 8px; }
.tag { display: inline-block; padding: 1px 6px; border-radius: 4px; background: #2f6fed; color: #fff; font-size: 12px; margin-right: 6px; }
.q-ans input, .q-ans select { width: 60%; padding: 6px; border: 1px solid #ccc; border-radius: 6px; }
.result { border: 1px solid #2f9e6e; border-radius: 8px; padding: 14px; background: #f2fbf6; }
.result h3 { margin-top: 0; }
.details { max-height: 260px; overflow: auto; }
.primary { margin-top: 14px; padding: 8px 22px; background: #2f6fed; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.primary:disabled { opacity: .6; cursor: not-allowed; }
.err { color: #e05b5b; }
</style>
