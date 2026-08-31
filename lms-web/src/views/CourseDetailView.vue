<script setup>
// 课程详情页：课程信息 + 点赞 + 课次学习 + 笔记 + 互动问答 + 两栏大纲（目录/正文）
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import gsap from 'gsap'
import { getCourseDetail, enrollCourse, quitCourse, changeCourseStatus, publishCourse, getCourseCatalog, getChapterContent, addCatalogNode, deleteCatalogNode, saveChapterContent, grabCourse, getGrabStatus } from '../api/course'
import { toggleLike, likeStatus } from '../api/like'
import { listLessons, recordLearning, listNotes, addNote, listQuestions, askQuestion, answerQuestion, signInCourse, coursePointsBoard, myCoursePoints, reportChapterRead } from '../api/learn'
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

// ---- 两栏大纲（0.2 课程内容域）----
const catalog = ref([])          // 左栏章节树
const currentChapter = ref(null) // 当前选中的目录节点
const chapterContent = ref('')   // 右栏 markdown 正文
const chapterHtml = ref('')      // 渲染后的正文 HTML

const panelEl = ref(null)
let gsapCtx = null

const load = async () => {
  try {
    course.value = await getCourseDetail(route.params.id)
    await Promise.all([loadLikes(), loadLessons(), loadNotes(), loadQuestions(), loadCatalog(), loadGrabStatus(), loadCoursePoints()])
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

// ---- 抢课（0.2 lms-grab）----
const grabStatus = ref(null)

const loadGrabStatus = async () => {
  try {
    grabStatus.value = await getGrabStatus(route.params.id)
  } catch (e) {
    grabStatus.value = null
  }
}

const onGrab = async () => {
  try {
    await grabCourse(course.value.id)
    alert('抢课成功，选课信息正在落库')
    loadGrabStatus()
    load()
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

// ---- 两栏大纲：目录树 + 章节正文 ----

/** 加载目录树（左栏） */
const loadCatalog = async () => {
  try {
    catalog.value = await getCourseCatalog(route.params.id)
  } catch (e) {
    catalog.value = []
  }
}

/** 点击章节加载正文（右栏 markdown） */
const selectChapter = async (node) => {
  currentChapter.value = node
  try {
    const data = await getChapterContent(node.id)
    chapterContent.value = data?.contentMd || ''
    chapterHtml.value = renderMarkdown(chapterContent.value)
    // 学生阅读章节 → 上报（首次阅读 +2 积分，幂等）
    if (isStudent() && course.value?.status === 3) {
      try {
        await reportChapterRead(route.params.id, node.id)
      } catch (e) { /* 上报失败不影响阅读 */ }
    }
  } catch (e) {
    chapterContent.value = ''
    chapterHtml.value = '<p class="empty-tip">正文加载失败或尚未编写</p>'
  }
}

// ---- 课程签到 + 积分榜（0.2 积分体系）----
const pointsBoard = ref([])
const myPointsInfo = ref(null)
const signedTip = ref('')

const loadCoursePoints = async () => {
  try {
    pointsBoard.value = await coursePointsBoard(route.params.id, { size: 10 })
    myPointsInfo.value = await myCoursePoints(route.params.id)
  } catch (e) {
    pointsBoard.value = []
    myPointsInfo.value = null
  }
}

const onSignInCourse = async () => {
  try {
    await signInCourse(route.params.id)
    signedTip.value = '签到成功，积分 +5'
    loadCoursePoints()
  } catch (e) {
    signedTip.value = e.message
  }
}

/** 极简 markdown → HTML（练手用，标题/段落/列表/代码/引用/加粗/图片/链接） */
const renderMarkdown = (md) => {
  if (!md) return '<p class="empty-tip">该章节暂无正文</p>'
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const inline = (s) => esc(s)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" target="_blank">$1</a>')
    .replace(/!\[([^\]]*)\]\((https?:[^)\s]+)\)/g, '<img src="$2" alt="$1" loading="lazy" />')
  const lines = md.split('\n')
  const out = []
  let inCode = false
  let inList = false
  let inQuote = false
  const flush = () => {
    if (inList) { out.push('</ul>'); inList = false }
    if (inQuote) { out.push('</blockquote>'); inQuote = false }
  }
  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '')
    if (line.startsWith('```')) {
      if (inCode) { out.push('</code></pre>'); inCode = false }
      else { flush(); out.push('<pre><code>'); inCode = true }
      continue
    }
    if (inCode) { out.push(esc(line) + '\n'); continue }
    const h = line.match(/^(#{1,4})\s+(.*)$/)
    if (h) { flush(); const level = h[1].length; out.push(`<h${level}>${inline(h[2])}</h${level}>`); continue }
    if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) { out.push('<ul>'); inList = true }
      out.push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ''))}</li>`); continue
    }
    if (line.startsWith('> ')) {
      if (!inQuote) { out.push('<blockquote>'); inQuote = true }
      out.push(`<p>${inline(line.slice(2))}</p>`); continue
    }
    if (!line.trim()) { flush(); continue }
    flush()
    out.push(`<p>${inline(line)}</p>`)
  }
  flush()
  if (inCode) out.push('</code></pre>')
  return out.join('\n')
}

const statusText = computed(() => {
  const map = { 0: '草稿', 1: '待发布', 2: '抢课中', 3: '进行中', 4: '已结束', 5: '已下架' }
  return map[course.value?.status] || '未知'
})

const onToggleStatus = async () => {
  try {
    await changeCourseStatus(course.value.id, course.value.status === 3 ? 5 : 3)
    load()
  } catch (e) {
    alert(e.message)
  }
}

const onPublish = async () => {
  const start = prompt('抢课开始时间（格式 2026-03-01T10:00:00）')
  if (!start) return
  const end = prompt('抢课结束时间（格式 2026-03-01T12:00:00）')
  if (!end) return
  const stock = prompt('抢课名额（0=不限）', '100') || '0'
  try {
    await publishCourse(course.value.id, { grabStartTime: start, grabEndTime: end, stock: Number(stock) })
    alert('发布成功（待抢课窗口开放）')
    load()
  } catch (e) {
    alert(e.message)
  }
}

// ---- 教师编辑大纲（新增章/节、删除、编辑正文）----

/** 新增章/节：parentId=0 为章，否则为节的父章 id */
const onAddNode = async () => {
  const level = prompt('新增层级（1=章 / 2=节）', '1')
  if (level !== '1' && level !== '2') return
  let parentId = 0
  if (level === '2') {
    const parent = currentChapter.value
    if (!parent || parent.level !== 1) {
      alert('新增节前，请先在左侧选中一个章')
      return
    }
    parentId = parent.id
  }
  const name = prompt('章节名称')
  if (!name) return
  try {
    await addCatalogNode(course.value.id, { parentId, name, level: Number(level) })
    loadCatalog()
  } catch (e) {
    alert(e.message)
  }
}

/** 删除当前章节（含子节与正文） */
const onDeleteNode = async () => {
  if (!currentChapter.value) {
    alert('请先在左侧选中要删除的章节')
    return
  }
  if (!confirm(`确定删除「${currentChapter.value.name}」及其子节/正文？`)) return
  try {
    await deleteCatalogNode(currentChapter.value.id)
    currentChapter.value = null
    chapterContent.value = ''
    chapterHtml.value = ''
    loadCatalog()
  } catch (e) {
    alert(e.message)
  }
}

/** 编辑当前章节正文（markdown 弹窗编辑） */
const onEditChapter = async () => {
  if (!currentChapter.value) {
    alert('请先在左侧选中要编辑的章节')
    return
  }
  const content = prompt('编辑章节正文（markdown）：', chapterContent.value || '')
  if (content === null) return
  try {
    await saveChapterContent(currentChapter.value.id, { contentMd: content })
    chapterContent.value = content
    chapterHtml.value = renderMarkdown(content)
    alert('正文已保存')
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
        <span :class="course.status === 3 ? 'online' : 'offline'">{{ statusText }}</span>
        <button v-btn-fx class="btn like-btn" :class="{ liked: likeState.liked }" @click="onToggleLike">
          {{ likeState.liked ? '已赞' : '点赞' }} ({{ likeState.likeCount }})
        </button>
      </div>
      <p class="intro">{{ course.intro || '暂无简介' }}</p>
      <div class="ops">
        <button v-btn-fx v-if="isStudent() && course.status === 2" class="btn btn-primary" @click="onGrab">
          {{ grabStatus?.grabbed ? '已抢到' : `抢课（剩余 ${grabStatus?.leftStock ?? '不限'} 名额）` }}
        </button>
        <button v-btn-fx v-if="isStudent() && course.status !== 2" class="btn btn-primary" @click="onEnroll">选课</button>
        <button v-btn-fx v-if="isStudent() && course.status === 3" class="btn" @click="onSignInCourse">课程签到（+5 积分）</button>
        <button v-btn-fx v-if="isStudent()" class="btn btn-danger" @click="onQuit">退课</button>
        <button v-btn-fx v-if="isTeacher()" class="btn" @click="onToggleStatus">
          {{ course.status === 3 ? '下架' : '上线' }}
        </button>
        <button v-btn-fx v-if="isTeacher() && course.status === 0" class="btn btn-primary" @click="onPublish">
          提交发布（设置抢课窗口）
        </button>
      </div>
    </div>

    <!-- 两栏大纲（0.2 课程内容域）：左目录树 + 右正文 -->
    <div class="detail-panel">
      <div class="outline-header">
        <h3 class="section-title">课程大纲</h3>
        <div v-if="isTeacher()" class="outline-ops">
          <button v-btn-fx class="btn" @click="onAddNode">新增章/节</button>
          <button v-btn-fx class="btn" @click="onEditChapter">编辑正文</button>
          <button v-btn-fx class="btn btn-danger" @click="onDeleteNode">删除章节</button>
        </div>
      </div>
      <div class="outline-layout">
        <div class="outline-left">
          <ul class="catalog-list">
            <li v-for="chapter in catalog" :key="chapter.id" class="catalog-chapter">
              <div class="catalog-title"
                   :class="{ active: currentChapter?.id === chapter.id }"
                   @click="selectChapter(chapter)">
                <span>{{ chapter.name }}</span>
              </div>
              <ul v-if="chapter.children?.length" class="catalog-sections">
                <li v-for="section in chapter.children" :key="section.id"
                    class="catalog-section"
                    :class="{ active: currentChapter?.id === section.id }"
                    @click="selectChapter(section)">
                  {{ section.name }}
                </li>
              </ul>
            </li>
            <li v-if="catalog.length === 0" class="empty-tip">暂无大纲（老师可先编辑课程内容）</li>
          </ul>
        </div>
        <div class="outline-right">
          <h4 class="chapter-name">{{ currentChapter?.name || '选择左侧章节查看内容' }}</h4>
          <div class="chapter-body" v-html="chapterHtml"></div>
        </div>
      </div>
    </div>

    <!-- 课程积分实时榜（0.2 ZSET） -->
    <div class="detail-panel">
      <div class="outline-header">
        <h3 class="section-title">课程积分榜（实时）</h3>
        <span v-if="myPointsInfo" class="my-rank">
          我的积分 {{ myPointsInfo.totalPoints ?? 0 }} · 排名 {{ myPointsInfo.rank ?? '未上榜' }}
        </span>
      </div>
      <span v-if="signedTip" class="signed-tip">{{ signedTip }}</span>
      <ul class="board-list">
        <li v-for="(item, idx) in pointsBoard" :key="item.userId" class="board-item">
          <span class="board-rank">{{ idx + 1 }}</span>
          <span class="board-user">用户 #{{ item.userId }}</span>
          <span class="board-points">{{ item.totalPoints }} 分</span>
        </li>
        <li v-if="pointsBoard.length === 0" class="empty-tip">暂无积分记录（阅读章节/考试/签到可获积分）</li>
      </ul>
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

/* 两栏大纲（0.2 课程内容域） */
.outline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.outline-header .section-title {
  margin-bottom: 0;
}

.outline-ops {
  display: flex;
  gap: 8px;
}

.outline-layout {
  display: flex;
  gap: 16px;
  min-height: 320px;
}

.outline-left {
  width: 260px;
  flex-shrink: 0;
  max-height: 480px;
  overflow-y: auto;
  border-right: 1px solid #f0f0f0;
  padding-right: 12px;
}

.outline-right {
  flex: 1;
  min-width: 0;
  padding: 0 4px;
  overflow-x: auto;
}

.catalog-list {
  list-style: none;
}

.catalog-chapter {
  margin-bottom: 6px;
}

.catalog-title {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.catalog-title:hover,
.catalog-section:hover {
  background: #f5f7fa;
}

.catalog-title.active,
.catalog-section.active {
  background: #ecf5ff;
  color: #409eff;
}

.catalog-sections {
  list-style: none;
  padding-left: 14px;
}

.catalog-section {
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
}

.chapter-name {
  margin-bottom: 10px;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 8px;
}

.chapter-body {
  font-size: 14px;
  line-height: 1.9;
  color: #444;
  word-break: break-word;
}

.chapter-body h1,
.chapter-body h2,
.chapter-body h3,
.chapter-body h4 {
  margin: 14px 0 8px;
  color: #303133;
}

.chapter-body p {
  margin: 8px 0;
}

.chapter-body pre {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
}

.chapter-body code {
  background: #f6f8fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 13px;
}

.chapter-body img {
  max-width: 100%;
  border-radius: 6px;
}

.chapter-body blockquote {
  border-left: 3px solid #409eff;
  margin: 8px 0;
  padding: 4px 12px;
  color: #909399;
  background: #f8fafc;
}

.empty-tip {
  color: #909399;
  font-size: 13px;
  padding: 8px 0;
}

/* 课程积分榜（0.2 ZSET） */
.my-rank {
  color: #409eff;
  font-size: 13px;
}

.signed-tip {
  display: inline-block;
  margin-bottom: 8px;
  color: #67c23a;
  font-size: 13px;
}

.board-list {
  list-style: none;
}

.board-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
}

.board-rank {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #606266;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.board-item:nth-child(1) .board-rank {
  background: #f56c6c;
  color: #fff;
}

.board-item:nth-child(2) .board-rank {
  background: #e6a23c;
  color: #fff;
}

.board-item:nth-child(3) .board-rank {
  background: #909399;
  color: #fff;
}

.board-user {
  flex: 1;
  color: #303133;
}

.board-points {
  color: #409eff;
  font-weight: 600;
}
</style>
