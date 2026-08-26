<script setup>
// 我的课程页：学生看选过的课，教师看自己创建的课（含未发布，可上下架）
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { queryEnrolledCourses, queryMyCourses, changeCourseStatus } from '../api/course'
import { isTeacher } from '../utils/auth'
import { useEntrance } from '../composables/useEntrance'
import CourseCard from '../components/CourseCard.vue'
import Pagination from '../components/Pagination.vue'

const router = useRouter()

const query = reactive({ pageNo: 1, pageSize: 8 })
const total = ref(0)
const list = ref([])
const loading = ref(false)

// 卡片网格容器：数据加载完成后播放入场动画
const gridEl = ref(null)
const { play: playEntrance } = useEntrance(gridEl)

const load = async () => {
  loading.value = true
  try {
    // 教师查自己创建的课程，学生查选过的课程
    const api = isTeacher() ? queryMyCourses : queryEnrolledCourses
    const data = await api({ pageNo: query.pageNo, pageSize: query.pageSize })
    total.value = data.total
    list.value = data.list
    await nextTick()
    playEntrance()
  } catch (e) {
    alert(e.message)
  } finally {
    loading.value = false
  }
}

const onPageChange = ({ pageNo, pageSize }) => {
  query.pageNo = pageNo
  query.pageSize = pageSize
  load()
}

// 教师上下架自己创建的课程
const onToggleStatus = async (course) => {
  try {
    await changeCourseStatus(course.id, course.status === 1 ? 0 : 1)
    load()
  } catch (e) {
    alert(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="page-title">{{ isTeacher() ? '我创建的课程' : '我选过的课程' }}</h2>
    <div v-if="loading" class="empty-tip">加载中...</div>
    <div v-else-if="list.length === 0" class="empty-tip">暂无课程</div>
    <div v-else ref="gridEl" class="card-grid">
      <div v-for="course in list" :key="course.id">
        <CourseCard :course="course">
          <button v-btn-fx class="btn" @click="router.push(`/courses/${course.id}`)">详情</button>
          <button v-btn-fx v-if="isTeacher()" class="btn" @click="onToggleStatus(course)">
            {{ course.status === 1 ? '下架' : '发布' }}
          </button>
        </CourseCard>
      </div>
    </div>
    <Pagination :total="total" :page-no="query.pageNo" :page-size="query.pageSize" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.page-title {
  margin-bottom: 16px;
  font-size: 18px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 900px) {
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
