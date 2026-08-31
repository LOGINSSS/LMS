<script setup>
// 课程列表页：卡片分页展示已发布课程；教师可建课/上下架，学生可选课
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { queryCoursePage, enrollCourse, addCourse } from '../api/course'
import { uploadMedia } from '../api/media'
import { isTeacher, isStudent } from '../utils/auth'
import { useEntrance } from '../composables/useEntrance'
import CourseCard from '../components/CourseCard.vue'
import Pagination from '../components/Pagination.vue'

const router = useRouter()

// 查询条件：分类 + 关键字 + 分页
const query = reactive({ pageNo: 1, pageSize: 8, category: '', keyword: '' })
const total = ref(0)
const list = ref([])
const loading = ref(false)

// 教师建课表单
const showAddForm = ref(false)
const courseForm = reactive({ name: '', cover: '', intro: '', category: '' })

// 卡片网格容器：数据加载完成后播放入场动画
const gridEl = ref(null)
const { play: playEntrance } = useEntrance(gridEl)

const load = async (withEntrance = true) => {
  loading.value = true
  try {
    const data = await queryCoursePage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      category: query.category || undefined,
      keyword: query.keyword || undefined
    })
    total.value = data.total
    list.value = data.list
    // 等 DOM 渲染完再播放入场（翻页/筛选也重播）
    if (withEntrance) {
      await nextTick()
      playEntrance()
    }
  } catch (e) {
    alert(e.message)
  } finally {
    loading.value = false
  }
}

// 搜索：条件变化回到第一页
const onSearch = () => {
  query.pageNo = 1
  load()
}

const onPageChange = ({ pageNo, pageSize }) => {
  query.pageNo = pageNo
  query.pageSize = pageSize
  load()
}

// 学生选课（卡片内点击）
const onEnroll = async (course) => {
  try {
    await enrollCourse(course.id)
    alert('选课成功')
    load()
  } catch (e) {
    alert(e.message)
  }
}

// 教师提交建课
const onSubmitCourse = async () => {
  if (!courseForm.name) {
    alert('请填写课程名称')
    return
  }
  try {
    await addCourse({
      name: courseForm.name,
      cover: courseForm.cover,
      intro: courseForm.intro,
      category: courseForm.category
    })
    alert('创建成功，默认草稿，请进入详情页编辑内容并发布')
    Object.assign(courseForm, { name: '', cover: '', intro: '', category: '' })
    showAddForm.value = false
  } catch (e) {
    alert(e.message)
  }
}

// 封面图上传（0.2 文件上传链路：前端 → lms-media → 回填 cover url）
const onCoverUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadMedia(file)
    courseForm.cover = res?.url || res?.data?.url || ''
    alert('封面上传成功')
  } catch (err) {
    alert('封面上传失败：' + err.message)
  } finally {
    e.target.value = ''
  }
}

onMounted(() => load())
</script>

<template>
  <div>
    <!-- 顶部工具栏：筛选 + 教师建课入口 -->
    <div class="toolbar">
      <div class="filters">
        <select v-model="query.category" @change="onSearch">
          <option value="">全部分类</option>
          <option value="微服务">微服务</option>
          <option value="前端">前端</option>
          <option value="数据库">数据库</option>
          <option value="AI">AI</option>
        </select>
        <input v-model="query.keyword" placeholder="搜索课程名称/简介" @keyup.enter="onSearch" />
        <button v-btn-fx class="btn btn-primary" @click="onSearch">搜索</button>
      </div>
      <button v-btn-fx v-if="isTeacher()" class="btn btn-primary" @click="showAddForm = !showAddForm">
        {{ showAddForm ? '收起' : '新建课程' }}
      </button>
    </div>

    <!-- 教师建课表单 -->
    <div v-if="showAddForm" class="panel add-form">
      <div class="form-item">
        <label>课程名称</label>
        <input v-model="courseForm.name" placeholder="必填" />
      </div>
      <div class="form-item">
        <label>封面图</label>
        <div class="cover-upload">
          <input v-model="courseForm.cover" placeholder="封面 URL（或上传图片自动回填）" />
          <label class="btn upload-btn">
            上传
            <input type="file" accept="image/*" hidden @change="onCoverUpload" />
          </label>
        </div>
      </div>
      <div class="form-item">
        <label>课程简介</label>
        <textarea v-model="courseForm.intro" rows="2" placeholder="可选"></textarea>
      </div>
      <div class="form-item">
        <label>分类</label>
        <input v-model="courseForm.category" placeholder="如 微服务" />
      </div>
      <button v-btn-fx class="btn btn-primary" @click="onSubmitCourse">创建课程</button>
    </div>

    <!-- 卡片网格 -->
    <div v-if="loading" class="empty-tip">加载中...</div>
    <div v-else-if="list.length === 0" class="empty-tip">暂无课程</div>
    <div v-else ref="gridEl" class="card-grid">
      <div v-for="course in list" :key="course.id">
        <CourseCard :course="course">
          <button v-btn-fx class="btn" @click="router.push(`/courses/${course.id}`)">详情</button>
          <button v-btn-fx v-if="isStudent()" class="btn btn-primary" @click="onEnroll(course)">选课</button>
          <button v-btn-fx v-if="isTeacher()" class="btn" @click="router.push(`/courses/${course.id}`)">管理</button>
        </CourseCard>
      </div>
    </div>

    <!-- 分页 -->
    <Pagination :total="total" :page-no="query.pageNo" :page-size="query.pageSize" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 8px;
}

.filters input {
  width: 220px;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.add-form {
  margin-bottom: 16px;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-item {
  flex: 1;
}

.cover-upload {
  display: flex;
  gap: 8px;
}

.cover-upload input {
  flex: 1;
}

.upload-btn {
  cursor: pointer;
  white-space: nowrap;
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
