<script setup>
// 课程管理布局页（教师）：页签导航 = 概览（信息/发布/危险操作）｜题库（本课程）｜出卷与考试作业｜知识库
// 子路由：/courses/:id/manage(+索引=概览)、/manage/questions、/manage/papers、/manage/kb
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourseDetail } from '../../api/course'
import { isTeacher } from '../../utils/auth'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

const courseName = ref('')
const loading = ref(true)

onMounted(async () => {
  if (!isTeacher()) {
    router.replace(`/courses/${courseId}`)
    return
  }
  try {
    const c = await getCourseDetail(courseId)
    courseName.value = c?.name || `课程 #${courseId}`
  } catch (e) {
    courseName.value = `课程 #${courseId}`
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="manage">
    <div class="head">
      <h2 v-if="!loading">{{ courseName }} <span class="sub">课程管理 #{{ courseId }}</span></h2>
      <h2 v-else class="sub">课程管理 #{{ courseId }}</h2>
      <div class="head-ops">
        <router-link v-btn-fx class="btn" :to="`/courses/${courseId}`">查看详情页</router-link>
        <router-link v-btn-fx class="btn" to="/my">返回我的课程</router-link>
      </div>
    </div>

    <nav class="tabs">
      <router-link :to="`/courses/${courseId}/manage`" class="tab" :class="{ on: route.name === 'course-manage' }">概览</router-link>
      <router-link :to="`/courses/${courseId}/manage/questions`" class="tab" :class="{ on: route.name === 'course-manage-questions' }">题库（本课程）</router-link>
      <router-link :to="`/courses/${courseId}/manage/papers`" class="tab" :class="{ on: route.name === 'course-manage-papers' }">出卷与考试作业</router-link>
      <router-link :to="`/courses/${courseId}/manage/kb`" class="tab" :class="{ on: route.name === 'course-manage-kb' }">知识库</router-link>
    </nav>

    <router-view />
  </div>
</template>

<style scoped>
.manage { display: flex; flex-direction: column; gap: 16px; }
.head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.head h2 { font-size: 18px; }
.head .sub { font-size: 13px; color: #999; font-weight: 400; }
.head-ops { display: flex; gap: 10px; }

.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #e3e6ea;
  background: #fff;
  border-radius: 8px 8px 0 0;
  padding: 0 12px;
}

.tab {
  padding: 10px 16px;
  font-size: 14px;
  color: #555;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab:hover { color: #409eff; }

.tab.on {
  color: #409eff;
  font-weight: 600;
  border-bottom-color: #409eff;
}
</style>
