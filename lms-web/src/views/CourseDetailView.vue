<script setup>
// 课程详情页：展示课程信息；学生可选课/退课，教师可上下架
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourseDetail, enrollCourse, quitCourse, changeCourseStatus } from '../api/course'
import { isTeacher, isStudent } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const course = ref(null)
const errorMsg = ref('')

const load = async () => {
  try {
    course.value = await getCourseDetail(route.params.id)
  } catch (e) {
    errorMsg.value = e.message
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
</script>

<template>
  <div v-if="errorMsg" class="empty-tip">{{ errorMsg }}</div>
  <div v-else-if="course" class="detail-panel">
    <button class="btn back-btn" @click="router.push('/courses')">返回列表</button>
    <div class="cover">
      <img v-if="course.cover" :src="course.cover" alt="课程封面" />
      <span v-else>暂无封面</span>
    </div>
    <h1>{{ course.name }}</h1>
    <div class="info">
      <span class="tag">{{ course.category }}</span>
      <span class="price">{{ Number(course.price) > 0 ? `¥${Number(course.price).toFixed(2)}` : '免费' }}</span>
      <span>教师：{{ course.teacherName }}</span>
      <span>{{ course.totalCount ?? 0 }} 人选课</span>
      <span :class="course.status === 1 ? 'online' : 'offline'">
        {{ course.status === 1 ? '已发布' : '已下架' }}
      </span>
    </div>
    <p class="intro">{{ course.intro || '暂无简介' }}</p>
    <div class="ops">
      <button v-if="isStudent()" class="btn btn-primary" @click="onEnroll">选课</button>
      <button v-if="isStudent()" class="btn btn-danger" @click="onQuit">退课</button>
      <button v-if="isTeacher()" class="btn" @click="onToggleStatus">
        {{ course.status === 1 ? '下架' : '发布' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.detail-panel {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.back-btn {
  margin-bottom: 16px;
}

.cover {
  height: 220px;
  background: #ecf5ff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a0cfff;
  margin-bottom: 16px;
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
}

.tag {
  padding: 1px 8px;
  border-radius: 3px;
  background: #f0f2f5;
}

.price {
  color: #f56c6c;
  font-weight: 600;
}

.online {
  color: #67c23a;
}

.offline {
  color: #999;
}

.intro {
  color: #666;
  line-height: 1.8;
  margin-bottom: 20px;
}

.ops {
  display: flex;
  gap: 10px;
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}
</style>
