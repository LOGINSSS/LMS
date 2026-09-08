<script setup>
// 课程广场页（搜索与推荐已并入此处，不再单列）：
// - 浏览模式：分类/分页展示已发布课程（教师可建课/管理，学生可选课）
// - 搜索模式：关键词走 ES 搜索（名称/简介）
// - 为你推荐：按兴趣标签个性化推荐（点击课程自动积累兴趣）
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { queryCoursePage, enrollCourse, addCourse, listCategories, addCategory } from '../api/course'
import { searchCourses, recommendCourses, recordInterest, myInterests } from '../api/search'
import { uploadMedia } from '../api/media'
import { isTeacher, isStudent } from '../utils/auth'
import { useEntrance } from '../composables/useEntrance'
import CourseCard from '../components/CourseCard.vue'
import Pagination from '../components/Pagination.vue'

const router = useRouter()

// 查询条件：模式 browse（数据库分页浏览）/ search（ES 关键词）
const query = reactive({ pageNo: 1, pageSize: 8, category: '', keyword: '' })
const mode = ref('browse')
const total = ref(0)
const list = ref([])
const loading = ref(false)
const errMsg = ref('')

// 兴趣推荐
const recommends = ref([])
const interests = ref([])

const isSearchMode = () => mode.value === 'search'

// 全局课程分类标签（下拉选择 + “+” 新增，所有教师共享）
const categories = ref([])
const loadCategories = async () => {
  try {
    categories.value = await listCategories()
  } catch (e) {
    categories.value = []
  }
}

// 教师在分类下拉里点“+ 添加新分类”
const onAddCategory = async () => {
  const name = prompt('新分类名称（将全局可见）：')
  if (!name || !name.trim()) return
  try {
    await addCategory(name.trim())
    await loadCategories()
    courseForm.category = name.trim()
    alert('分类「' + name.trim() + '」已添加')
  } catch (e) {
    alert(e.message)
  }
}

// 教师建课表单
const showAddForm = ref(false)
const courseForm = reactive({ name: '', cover: '', intro: '', category: '' })

// 卡片网格容器：数据加载完成后播放入场动画
const gridEl = ref(null)
const { play: playEntrance } = useEntrance(gridEl)

const load = async (withEntrance = true) => {
  loading.value = true
  errMsg.value = ''
  try {
    let data
    if (isSearchMode()) {
      data = await searchCourses({
        keyword: query.keyword.trim() || undefined,
        category: query.category || undefined,
        pageNo: query.pageNo,
        pageSize: query.pageSize
      })
    } else {
      data = await queryCoursePage({
        pageNo: query.pageNo,
        pageSize: query.pageSize,
        category: query.category || undefined
      })
    }
    total.value = data.total
    list.value = data.list
    // 等 DOM 渲染完再播放入场（翻页/筛选也重播）
    if (withEntrance) {
      await nextTick()
      playEntrance()
    }
  } catch (e) {
    errMsg.value = e.message
  } finally {
    loading.value = false
  }
}

// 搜索：有关键词走 ES 搜索，清空则回浏览模式
const onSearch = () => {
  mode.value = query.keyword.trim() ? 'search' : 'browse'
  query.pageNo = 1
  load()
}

const onReset = () => {
  query.keyword = ''
  query.category = ''
  mode.value = 'browse'
  query.pageNo = 1
  load()
}

const onPageChange = ({ pageNo, pageSize }) => {
  query.pageNo = pageNo
  query.pageSize = pageSize
  load()
}

// 打开课程：上报兴趣标签（权重累加，用于个性化推荐），失败不影响跳转
const openCourse = async (course) => {
  if (course.category) {
    try {
      await recordInterest(course.category)
    } catch (e) {
      // 忽略
    }
  }
  router.push(`/courses/${course.id}`)
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

// 兴趣推荐加载（失败静默）
const loadRecommend = async () => {
  try {
    const tags = await myInterests()
    interests.value = Array.isArray(tags) ? tags : []
  } catch (e) {
    interests.value = []
  }
  try {
    recommends.value = await recommendCourses(6)
    if (!Array.isArray(recommends.value)) recommends.value = []
  } catch (e) {
    recommends.value = []
  }
}

onMounted(() => {
  loadCategories()
  loadRecommend()
  load()
})
</script>

<template>
  <div>
    <h2 class="page-title">课程广场</h2>

    <!-- 顶部工具栏：分类 + 关键词搜索（ES）+ 教师建课入口 -->
    <div class="toolbar">
      <div class="filters">
        <select v-model="query.category" @change="onSearch">
          <option value="">全部分类</option>
          <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
        </select>
        <input v-model="query.keyword" placeholder="关键词搜索课程名称/简介" @keyup.enter="onSearch" />
        <button v-btn-fx class="btn btn-primary" @click="onSearch">搜索</button>
        <button v-btn-fx v-if="isSearchMode() || query.category" class="btn" @click="onReset">清除筛选</button>
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
        <div class="cat-picker">
          <select v-model="courseForm.category">
            <option value="" disabled>请选择分类</option>
            <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
          </select>
          <button v-btn-fx type="button" class="btn" title="添加新分类（全局可见）" @click="onAddCategory">＋</button>
        </div>
      </div>
      <button v-btn-fx class="btn btn-primary" @click="onSubmitCourse">创建课程</button>
    </div>

    <!-- 为你推荐（仅浏览模式展示；点击课程积累兴趣标签） -->
    <div v-if="!isSearchMode() && recommends.length" class="panel rec-box">
      <h3 class="section-title">为你推荐
        <span v-if="interests.length" class="rec-sub">（我的兴趣：{{ interests.join('、') }}）</span>
        <span v-else class="rec-sub">点击课程卡片积累兴趣，推荐会更懂你</span>
      </h3>
      <div class="card-grid">
        <div v-for="course in recommends" :key="`r-${course.id}`">
          <CourseCard :course="course">
            <button v-btn-fx class="btn btn-primary" @click="openCourse(course)">查看</button>
          </CourseCard>
        </div>
      </div>
    </div>

    <!-- 列表标题 -->
    <div class="list-head">
      <h3 class="section-title">{{ isSearchMode() ? '搜索结果' : '全部课程' }}</h3>
      <span class="count">共 {{ total }} 门</span>
    </div>

    <!-- 卡片网格 -->
    <p v-if="loading" class="empty-tip">加载中...</p>
    <p v-else-if="errMsg" class="empty-tip">{{ errMsg }}</p>
    <div v-else-if="list.length === 0" class="empty-tip">
      {{ isSearchMode() ? '未找到相关课程，换个关键词试试' : '暂无课程' }}
    </div>
    <div v-else ref="gridEl" class="card-grid">
      <div v-for="course in list" :key="course.id">
        <CourseCard :course="course">
          <button v-btn-fx class="btn" @click="openCourse(course)">详情</button>
          <button v-btn-fx v-if="isStudent()" class="btn btn-primary" @click="onEnroll(course)">选课</button>
          <button v-btn-fx v-if="isTeacher()" class="btn" @click="router.push(`/courses/${course.id}/manage`)">管理</button>
        </CourseCard>
      </div>
    </div>

    <!-- 分页 -->
    <Pagination :total="total" :page-no="query.pageNo" :page-size="query.pageSize" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.page-title {
  margin-bottom: 16px;
  font-size: 18px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 10px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filters input {
  width: 240px;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.add-form {
  margin-bottom: 16px;
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

.cat-picker {
  display: flex;
  gap: 8px;
}

.cat-picker select {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

/* 推荐区 */
.rec-box {
  margin-bottom: 8px;
}

.rec-sub {
  color: #999;
  font-size: 12px;
  font-weight: 400;
  margin-left: 6px;
}

.list-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 16px 0 12px;
}

.list-head .section-title {
  margin: 0;
}

.count {
  color: #999;
  font-size: 13px;
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
