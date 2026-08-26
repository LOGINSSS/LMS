<script setup>
// 课程详情页：课程信息 + 点赞 + 课次学习 + 笔记 + 互动问答
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import gsap from 'gsap'
import { getCourseDetail, enrollCourse, quitCourse, changeCourseStatus } from '../api/course'
import { toggleLike, likeStatus } from '../api/like'
import { listLessons, recordLearning, listNotes, addNote, listQuestions, askQuestion, answerQuestion } from '../api/learn'
import { isTeacher, isStudent } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const course = ref(null)
const errorMsg = ref('')
const likeState = ref({ liked: false, likeCount: 0 })

const lessons = ref([])
const notes = ref([])
const noteContent = ref('')
const questions = ref([])
const qaForm = ref({ title: '', content: '' })

const panelEl = ref(null)
let gsapCtx = null

const load = async () => {
  try {
    course.value = await getCourseDetail(route.params.id)
    await Promise.all([loadLikes(), loadLessons(), loadNotes(), loadQuestions()])
    await nextTick()
    gsapCtx = gsap.context(() => {
      gsap.fromTo(panelEl.value,
        { y: 20, opacity: 0 },
        { y: 0, opacity: 1, duration: 0.5, ease: 'power2.out' })
    }, panelEl.value)
  } catch (e) {
    errorMsg.value = e.message
  }
}

// 点赞：查询状态（bizType=1 课程）
const loadLikes = async () => {
  try {
    likeState.value = await likeStatus(1, route.params.id)
  } catch (e) {
    // 点赞服务不可用不影响详情
  }
}

const onToggleLike = async () => {
  try {
    likeState.value = await toggleLike(1, route.params.id)
  } catch (e) {
    alert(e.message)
  }
}

// 课次
const loadLessons = async () => {
  lessons.value = await listLessons(route.params.id)
}

const onLearn = async (lesson) => {
  try {
    await recordLearning({ lessonId: lesson.id, progress: 100 })
    alert(`完成「${lesson.name}」，学习积分 +2（首次）`)
  } catch (e) {
    alert(e.message)
  }
}

// 笔记
const loadNotes = async () => {
  const data = await listNotes({ courseId: route.params.id, pageNo: 1, pageSize: 20 })
  notes.value = data.list
}

const onSubmitNote = async () => {
  if (!noteContent.value.trim()) return
  try {
    await addNote({ courseId: route.params.id, content: noteContent.value })
    noteContent.value = ''
    loadNotes()
  } catch (e) {
    alert(e.message)
  }
}

// 问答
const loadQuestions = async () => {
  const data = await listQuestions({ courseId: route.params.id, pageNo: 1, pageSize: 20 })
  questions.value = data.list
}

const onSubmitQuestion = async () => {
  if (!qaForm.value.title.trim()) return
  try {
    await askQuestion({ courseId: route.params.id, title: qaForm.value.title, content: qaForm.value.content })
    qaForm.value = { title: '', content: '' }
    loadQuestions()
  } catch (e) {
    alert(e.message)
  }
}

const onAnswer = async (question) => {
  const content = prompt('输入你的回答')
  if (!content) return
  try {
    await answerQuestion(question.id, { content })
    loadQuestions()
  } catch (e) {
    alert(e.message)
  }
}

const onEnroll = async () => {
  try {
    await enrollCourse(course.value.id)
    alert('选课成功')
  } catch (e) {
    alert(e.message)
  }
}

const onQuit = async () => {
  try {
    await quitCourse(course.value.id)
    alert('退课成功')
    load()
  } catch (e) {
    alert(e.message)
  }
}

const onToggleStatus = async () => {
  try {
    await changeCourseStatus(course.value.id, course.value.status === 1 ? 0 : 1)
    load()
  } catch (e) {
    alert(e.message)
  }
}

onMounted(load)

onUnmounted(() => {
  if (gsapCtx) gsapCtx.revert()
})
</script>

<template>
  <div v-if="errorMsg" class="empty-tip">{{ errorMsg }}</div>
  <div v-else-if="course" ref="panelEl">
    <div class="detail-panel">
      <button v-btn-fx class="btn back-btn" @click="router.push('/courses')">返回列表</button>
      <div class="cover">
        <img v-if="course.cover" :src="course.cover" alt="课程封面" />
        <span v-else>暂无封面</span>
      </div>
      <h1>{{ course.name }}</h1>
      <div class="info">
        <span class="tag">{{ course.category }}</span>
        <span>教师：{{ course.teacherName }}</span>
        <span>{{ course.totalCount ?? 0 }} 人选课</span>
        <span :class="course.status === 1 ? 'online' : 'offline'">
          {{ course.status === 1 ? '已发布' : '已下架' }}
        </span>
        <button v-btn-fx class="btn like-btn" :class="{ liked: likeState.liked }" @click="onToggleLike">
          {{ likeState.liked ? '已赞' : '点赞' }} ({{ likeState.likeCount }})
        </button>
      </div>
      <p class="intro">{{ course.intro || '暂无简介' }}</p>
      <div class="ops">
        <button v-btn-fx v-if="isStudent()" class="btn btn-primary" @click="onEnroll">选课</button>
        <button v-btn-fx v-if="isStudent()" class="btn btn-danger" @click="onQuit">退课</button>
        <button v-btn-fx v-if="isTeacher()" class="btn" @click="onToggleStatus">
          {{ course.status === 1 ? '下架' : '发布' }}
        </button>
      </div>
    </div>

    <div class="detail-panel">
      <h3 class="section-title">课次（点击学习，首次 +2 积分）</h3>
      <ul class="lesson-list">
        <li v-for="lesson in lessons" :key="lesson.id" class="lesson-item">
          <span>{{ lesson.sort ?? 0 }}. {{ lesson.name }}</span>
          <button v-btn-fx class="btn btn-primary" @click="onLearn(lesson)">学习</button>
        </li>
        <li v-if="lessons.length === 0" class="empty-tip">暂无课次</li>
      </ul>
    </div>

    <div class="detail-panel">
      <h3 class="section-title">学习笔记</h3>
      <div class="note-form">
        <input v-model="noteContent" placeholder="记录你的学习笔记..." @keyup.enter="onSubmitNote" />
        <button v-btn-fx class="btn btn-primary" @click="onSubmitNote">发布</button>
      </div>
      <ul class="note-list">
        <li v-for="note in notes" :key="note.id" class="note-item">{{ note.content }}</li>
        <li v-if="notes.length === 0" class="empty-tip">暂无笔记</li>
      </ul>
    </div>

    <div class="detail-panel">
      <h3 class="section-title">互动问答（提问 +3 积分）</h3>
      <div class="qa-form">
        <input v-model="qaForm.title" placeholder="问题标题" />
        <input v-model="qaForm.content" placeholder="问题详情（可选）" />
        <button v-btn-fx class="btn btn-primary" @click="onSubmitQuestion">提问</button>
      </div>
      <div v-for="q in questions" :key="q.id" class="qa-item">
        <p class="qa-title">{{ q.title }} <span class="qa-sub">by 用户 #{{ q.userId }}</span></p>
        <p v-if="q.content" class="qa-content">{{ q.content }}</p>
        <ul class="answer-list">
          <li v-for="a in q.answers" :key="a.id" class="answer-item">
            <span>用户 #{{ a.userId }}：{{ a.content }}</span>
            <span v-if="a.accepted === 1" class="accepted-tag">已采纳</span>
          </li>
        </ul>
        <button v-btn-fx class="btn" @click="onAnswer(q)">回答（+5 积分）</button>
      </div>
      <div v-if="questions.length === 0" class="empty-tip">暂无提问</div>
    </div>
  </div>
</template>

<style scoped>
.detail-panel {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  margin-bottom: 16px;
}

.back-btn {
  margin-bottom: 16px;
}

.cover {
  height: 200px;
  background: #ecf5ff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a0cfff;
  margin-bottom: 14px;
  overflow: hidden;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info {
  display: flex;
  gap: 16px;
  align-items: center;
  margin: 12px 0;
  color: #666;
  font-size: 14px;
  flex-wrap: wrap;
}

.tag {
  padding: 1px 8px;
  border-radius: 3px;
  background: #f0f2f5;
}

.online {
  color: #67c23a;
}

.offline {
  color: #999;
}

.like-btn.liked {
  color: #f56c6c;
  border-color: #f56c6c;
}

.intro {
  color: #666;
  line-height: 1.8;
  margin-bottom: 16px;
}

.ops {
  display: flex;
  gap: 10px;
  border-top: 1px solid #f0f0f0;
  padding-top: 14px;
}

.section-title {
  margin-bottom: 10px;
  font-size: 16px;
}

.lesson-list,
.note-list,
.answer-list {
  list-style: none;
}

.lesson-item,
.note-item,
.answer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
}

.note-form,
.qa-form {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.note-form input,
.qa-form input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.qa-item {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.qa-title {
  font-size: 14px;
  font-weight: 600;
}

.qa-sub {
  color: #999;
  font-size: 12px;
  font-weight: 400;
}

.qa-content {
  color: #666;
  font-size: 13px;
  margin: 6px 0;
}

.answer-item {
  font-size: 13px;
  color: #555;
}

.accepted-tag {
  color: #67c23a;
  font-size: 12px;
  border: 1px solid #67c23a;
  border-radius: 3px;
  padding: 0 4px;
}
</style>
