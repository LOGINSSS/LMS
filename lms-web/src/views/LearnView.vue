<script setup>
// 学习中心页：选课学习（课次/进度上报）、每日签到、积分明细与积分榜
import { ref, onMounted } from 'vue'
import { queryEnrolledCourses, queryMyCourses } from '../api/course'
import { listLessons, recordLearning, getCourseProgress, signIn, myPoints, pointsBoard } from '../api/learn'
import { isTeacher } from '../utils/auth'

const courses = ref([])
const currentCourseId = ref(null)
const lessons = ref([])
const progress = ref(0)
const points = ref([])
const board = ref([])
const signMsg = ref('')

const loadCourses = async () => {
  // 教师看自己创建的课程，学生看选过的课程
  const api = isTeacher() ? queryMyCourses : queryEnrolledCourses
  const data = await api({ pageNo: 1, pageSize: 50 })
  courses.value = data.list
  if (courses.value.length > 0) {
    currentCourseId.value = courses.value[0].id
    await loadCourseDetail(courses.value[0].id)
  }
}

const loadCourseDetail = async (courseId) => {
  const [ls, pg] = await Promise.all([listLessons(courseId), getCourseProgress(courseId)])
  lessons.value = ls
  progress.value = pg
}

const onSelectCourse = async () => {
  await loadCourseDetail(currentCourseId.value)
}

// 学习课次：上报进度（首次学习自动发放学习积分）
const onLearn = async (lesson) => {
  try {
    await recordLearning({ lessonId: lesson.id, progress: 100 })
    alert(`已完成「${lesson.name}」，学习积分 +2（首次）`)
    loadCourseDetail(currentCourseId.value)
  } catch (e) {
    alert(e.message)
  }
}

// 每日签到
const onSignIn = async () => {
  try {
    await signIn()
    signMsg.value = '签到成功，积分 +5'
    loadPoints()
  } catch (e) {
    signMsg.value = e.message
  }
}

const loadPoints = async () => {
  const [p, b] = await Promise.all([
    myPoints({ pageNo: 1, pageSize: 10 }),
    pointsBoard({ size: 10 })
  ])
  points.value = p.list
  board.value = b
}

onMounted(async () => {
  await loadCourses()
  await loadPoints()
})
</script>

<template>
  <div>
    <div class="panel">
      <div class="toolbar">
        <label class="label">选择课程</label>
        <select v-model="currentCourseId" class="course-select" @change="onSelectCourse">
          <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <button v-btn-fx class="btn btn-primary" @click="onSignIn">每日签到</button>
        <span v-if="signMsg" class="sign-msg">{{ signMsg }}</span>
      </div>
      <p class="progress-tip">课程学习进度：{{ progress }}%（已学课次 / 总课次）</p>
      <div class="progress-bar">
        <div class="progress-inner" :style="{ width: progress + '%' }"></div>
      </div>
    </div>

    <div class="panel">
      <h3 class="section-title">课次列表</h3>
      <ul class="lesson-list">
        <li v-for="lesson in lessons" :key="lesson.id" class="lesson-item">
          <span class="lesson-name">{{ lesson.sort ?? 0 }}. {{ lesson.name }}</span>
          <button v-btn-fx class="btn btn-primary" @click="onLearn(lesson)">学习（+2 积分）</button>
        </li>
        <li v-if="lessons.length === 0" class="empty-tip">该课程暂无课次</li>
      </ul>
    </div>

    <div class="two-col">
      <div class="panel">
        <h3 class="section-title">我的积分明细</h3>
        <ul class="rank-list">
          <li v-for="p in points" :key="p.id" class="point-item">
            <span class="name">类型 #{{ p.type }}</span>
            <span class="count">+{{ p.points }}</span>
          </li>
          <li v-if="points.length === 0" class="empty-rank">暂无积分记录</li>
        </ul>
      </div>
      <div class="panel">
        <h3 class="section-title">积分榜</h3>
        <ul class="rank-list">
          <li v-for="(b, i) in board" :key="b.userId" class="point-item">
            <span class="rank">{{ i + 1 }}</span>
            <span class="name">用户 #{{ b.userId }}</span>
            <span class="count">{{ b.totalPoints }} 分</span>
          </li>
          <li v-if="board.length === 0" class="empty-rank">暂无数据</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.label {
  color: #666;
  font-size: 14px;
}

.course-select {
  flex: 1;
  max-width: 320px;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.sign-msg {
  color: #67c23a;
  font-size: 13px;
}

.progress-tip {
  color: #666;
  font-size: 13px;
  margin-top: 14px;
}

.progress-bar {
  height: 8px;
  background: #f0f2f5;
  border-radius: 4px;
  margin-top: 6px;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  background: #409eff;
  border-radius: 4px;
  transition: width 0.4s ease;
}

.section-title {
  margin-bottom: 10px;
  font-size: 16px;
}

.lesson-list {
  list-style: none;
}

.lesson-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.lesson-name {
  font-size: 14px;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.rank-list {
  list-style: none;
}

.rank-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
}

.rank {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: #ecf5ff;
  color: #409eff;
  text-align: center;
  line-height: 22px;
  font-size: 12px;
}

.point-item .name {
  flex: 1;
}

.point-item .count {
  color: #67c23a;
  font-weight: 600;
}

.empty-rank {
  color: #999;
}
</style>
