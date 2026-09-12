<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getCourseDetail } from '../api/course'
import { answerQuestion, askQuestion, listQuestions } from '../api/learn'
import Pagination from '../components/Pagination.vue'
import QuestionThread from '../components/qa/QuestionThread.vue'
import { isStudent, isTeacher } from '../utils/auth'
import { filterCourseQuestions } from '../utils/courseQa'

const route = useRoute()
const course = ref(null)
const questions = ref([])
const loading = ref(true)
const errorMsg = ref('')
const successMsg = ref('')
const activeFilter = ref('all')
const pageNo = ref(1)
const pageSize = 20
const total = ref(0)
const submittingQuestion = ref(false)
const submittingAnswerId = ref(null)
const form = ref({ title: '', content: '' })

const targetQuestionId = computed(() => String(route.query.questionId || ''))
const filteredQuestions = computed(() => filterCourseQuestions(questions.value, activeFilter.value))
const pendingCount = computed(() => filterCourseQuestions(questions.value, 'pending').length)

const focusTargetQuestion = async () => {
  if (!targetQuestionId.value) return
  await nextTick()
  document.getElementById(`question-${targetQuestionId.value}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

const loadQuestions = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await listQuestions({ courseId: route.params.id, pageNo: pageNo.value, pageSize })
    questions.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total) || 0
    await focusTargetQuestion()
  } catch (error) {
    errorMsg.value = error.message || '问答记录加载失败'
  } finally {
    loading.value = false
  }
}

const load = async () => {
  try {
    course.value = await getCourseDetail(route.params.id)
    await loadQuestions()
  } catch (error) {
    errorMsg.value = error.message || '课程问答加载失败'
    loading.value = false
  }
}

const submitQuestion = async () => {
  const title = form.value.title.trim()
  if (!title) return
  submittingQuestion.value = true
  errorMsg.value = ''
  try {
    await askQuestion({ courseId: Number(route.params.id), title, content: form.value.content.trim() })
    form.value = { title: '', content: '' }
    pageNo.value = 1
    activeFilter.value = 'all'
    successMsg.value = '问题已发布，课程教师会收到通知。'
    await loadQuestions()
  } catch (error) {
    errorMsg.value = error.message || '问题发布失败'
  } finally {
    submittingQuestion.value = false
  }
}

const submitAnswer = async ({ questionId, content, done }) => {
  submittingAnswerId.value = questionId
  errorMsg.value = ''
  try {
    await answerQuestion(questionId, { content })
    done()
    successMsg.value = '回答已发布，并已通知提问学生。'
    await loadQuestions()
  } catch (error) {
    errorMsg.value = error.message || '回答发布失败'
  } finally {
    submittingAnswerId.value = null
  }
}

const changePage = ({ pageNo: nextPage }) => {
  pageNo.value = nextPage
  loadQuestions()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(() => route.query.questionId, focusTargetQuestion)
onMounted(load)
</script>

<template>
  <div class="qa-page">
    <header class="qa-header">
      <div class="breadcrumbs">
        <router-link :to="`/courses/${route.params.id}`">{{ course?.name || '课程详情' }}</router-link>
        <span>/</span>
        <span>问答中心</span>
      </div>
      <div class="header-main">
        <div>
          <h1>课程问答</h1>
          <p>围绕课程内容沉淀问题与解答，历史讨论会持续保留。</p>
        </div>
        <router-link v-btn-fx class="btn" :to="`/courses/${route.params.id}`">返回课程</router-link>
      </div>
    </header>

    <section v-if="isStudent()" class="ask-panel" aria-labelledby="ask-title">
      <div>
        <h2 id="ask-title">提出新问题</h2>
        <p>描述清楚遇到的知识点或具体步骤，教师会在这里集中回答。</p>
      </div>
      <div class="ask-form">
        <label for="question-title">问题标题</label>
        <input id="question-title" v-model="form.title" maxlength="255" placeholder="例如：这里为什么需要开启事务？" />
        <label for="question-content">问题详情</label>
        <textarea id="question-content" v-model="form.content" rows="4" maxlength="1000" placeholder="补充你的理解、已经尝试过的方法或相关章节。"></textarea>
        <div class="ask-foot">
          <span>发布问题可获得 3 积分</span>
          <button v-btn-fx class="btn btn-primary" :disabled="submittingQuestion || !form.title.trim()" @click="submitQuestion">
            {{ submittingQuestion ? '发布中…' : '发布问题' }}
          </button>
        </div>
      </div>
    </section>

    <p v-if="successMsg" class="feedback success" role="status">{{ successMsg }}</p>
    <p v-if="errorMsg" class="feedback error" role="alert">{{ errorMsg }}</p>

    <section class="history-panel" aria-labelledby="history-title">
      <div class="history-head">
        <div>
          <h2 id="history-title">历史问答</h2>
          <p>共 {{ total }} 个问题<span v-if="isTeacher()">，当前页 {{ pendingCount }} 个待回答</span></p>
        </div>
        <div class="filters" aria-label="问答状态筛选">
          <button v-for="item in [{ key: 'all', label: '全部' }, { key: 'pending', label: '待回答' }, { key: 'answered', label: '已回答' }]"
                  :key="item.key" type="button" :class="{ active: activeFilter === item.key }" @click="activeFilter = item.key">
            {{ item.label }}
          </button>
        </div>
      </div>

      <div v-if="loading" class="state-card" aria-live="polite">正在加载问答记录…</div>
      <div v-else-if="!filteredQuestions.length" class="state-card">
        {{ activeFilter === 'all' ? '还没有人提问，成为第一个发起讨论的人吧。' : '当前页没有符合条件的问题。' }}
      </div>
      <div v-else class="thread-list">
        <QuestionThread
          v-for="question in filteredQuestions"
          :key="question.id"
          :question="question"
          :can-answer="isTeacher()"
          :highlighted="String(question.id) === targetQuestionId"
          :submitting="submittingAnswerId === question.id"
          @answer="submitAnswer"
        />
      </div>

      <Pagination v-if="total > pageSize" :total="total" :page-no="pageNo" :page-size="pageSize" @page-change="changePage" />
    </section>
  </div>
</template>

<style scoped>
.qa-page { padding: 8px 0 40px; }
.qa-header { margin-bottom: 18px; padding: 26px 28px; border-bottom: 3px solid #409eff; background: #fff; }
.breadcrumbs { display: flex; gap: 8px; margin-bottom: 20px; color: #8a99a8; font-size: 13px; }
.header-main { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.header-main h1 { margin-bottom: 8px; color: #24364b; font-size: 30px; }
.header-main p, .ask-panel > div > p, .history-head p { color: #7c8c9c; line-height: 1.6; }

.ask-panel, .history-panel { background: #fff; border: 1px solid #e5eaf0; border-radius: 8px; }
.ask-panel { display: grid; grid-template-columns: minmax(180px, 0.32fr) 1fr; gap: 32px; margin-bottom: 18px; padding: 24px 28px; }
.ask-panel h2, .history-head h2 { margin-bottom: 6px; color: #2a3d52; font-size: 19px; }
.ask-form label { display: block; margin-bottom: 6px; color: #536579; font-size: 13px; font-weight: 600; }
.ask-form input, .ask-form textarea { width: 100%; margin-bottom: 14px; padding: 11px 13px; border: 1px solid #cfd9e5; border-radius: 6px; font: inherit; }
.ask-form textarea { resize: vertical; }
.ask-form input:focus, .ask-form textarea:focus { outline: 2px solid rgba(64, 158, 255, 0.18); border-color: #409eff; }
.ask-foot { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.ask-foot span { color: #8492a6; font-size: 13px; }

.feedback { margin-bottom: 16px; padding: 12px 16px; border-radius: 6px; font-size: 14px; }
.feedback.success { background: #edf8f2; color: #277a50; }
.feedback.error { background: #fff0f0; color: #b94a48; }
.history-panel { padding: 24px 28px; }
.history-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.filters { display: flex; padding: 3px; border: 1px solid #dfe6ee; border-radius: 6px; background: #f6f8fa; }
.filters button { padding: 6px 14px; border: 0; border-radius: 4px; background: transparent; color: #607286; cursor: pointer; }
.filters button.active { background: #fff; color: #2678bf; box-shadow: 0 1px 3px rgba(33, 61, 84, 0.12); font-weight: 600; }
.thread-list { display: grid; gap: 14px; }
.state-card { padding: 48px 20px; border: 1px dashed #d7e0e9; background: #fafbfd; color: #8492a6; text-align: center; }

@media (max-width: 720px) {
  .qa-header, .ask-panel, .history-panel { padding: 20px 16px; }
  .header-main, .history-head { align-items: flex-start; flex-direction: column; }
  .ask-panel { grid-template-columns: 1fr; gap: 18px; }
  .filters { width: 100%; }
  .filters button { flex: 1; }
}
</style>
