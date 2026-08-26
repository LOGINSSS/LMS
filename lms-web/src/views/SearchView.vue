<script setup>
// 搜索与推荐页：ES 课程搜索、按兴趣推荐、兴趣标签上报
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { searchCourses, recommendCourses, recordInterest, myInterests } from '../api/search'
import CourseCard from '../components/CourseCard.vue'
import Pagination from '../components/Pagination.vue'

const router = useRouter()

const query = reactive({ keyword: '', category: '', pageNo: 1, pageSize: 8 })
const total = ref(0)
const list = ref([])
const recommends = ref([])
const interests = ref([])
const currentTag = ref('')

// 搜索
const onSearch = async () => {
  query.pageNo = 1
  const data = await searchCourses({
    keyword: query.keyword || undefined,
    category: query.category || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize
  })
  total.value = data.total
  list.value = data.list
}

const onPageChange = ({ pageNo, pageSize }) => {
  query.pageNo = pageNo
  query.pageSize = pageSize
  onSearch()
}

// 按兴趣标签推荐
const loadRecommend = async () => {
  recommends.value = await recommendCourses(6)
  interests.value = await myInterests()
  currentTag.value = interests.value[0] || ''
}

// 点击推荐/搜索结果卡片时上报兴趣标签（权重累加，前端选课/浏览行为简化上报）
const onOpenCourse = async (course) => {
  if (course.category) {
    try {
      await recordInterest(course.category)
    } catch (e) {
      // 上报失败不影响跳转
    }
  }
  router.push(`/courses/${course.id}`)
}

onMounted(async () => {
  await onSearch()
  await loadRecommend()
})
</script>

<template>
  <div>
    <div class="panel search-box">
      <div class="filters">
        <input v-model="query.keyword" placeholder="搜索课程名称/简介" @keyup.enter="onSearch" />
        <select v-model="query.category" @change="onSearch">
          <option value="">全部分类</option>
          <option value="微服务">微服务</option>
          <option value="前端">前端</option>
          <option value="数据库">数据库</option>
          <option value="AI">AI</option>
        </select>
        <button v-btn-fx class="btn btn-primary" @click="onSearch">搜索</button>
      </div>
      <p v-if="currentTag" class="interest-tip">我的兴趣：{{ interests.join('、') }}，已按「{{ currentTag }}」推荐</p>
      <p v-else class="interest-tip">点击课程卡片即可积累兴趣标签，用于个性化推荐</p>
    </div>

    <h3 class="section-title">为你推荐</h3>
    <div v-if="recommends.length === 0" class="empty-tip">暂无推荐（先积累兴趣标签）</div>
    <div v-else class="card-grid">
      <div v-for="course in recommends" :key="`r-${course.id}`">
        <CourseCard :course="course">
          <button v-btn-fx class="btn" @click="onOpenCourse(course)">查看</button>
        </CourseCard>
      </div>
    </div>

    <h3 class="section-title">搜索结果（{{ total }}）</h3>
    <div v-if="list.length === 0" class="empty-tip">未找到相关课程</div>
    <div v-else class="card-grid">
      <div v-for="course in list" :key="course.id">
        <CourseCard :course="course">
          <button v-btn-fx class="btn" @click="onOpenCourse(course)">查看</button>
        </CourseCard>
      </div>
    </div>

    <Pagination :total="total" :page-no="query.pageNo" :page-size="query.pageSize" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.search-box {
  padding: 16px 20px;
}

.filters {
  display: flex;
  gap: 8px;
}

.filters input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.interest-tip {
  color: #999;
  font-size: 13px;
  margin-top: 10px;
}

.section-title {
  margin: 18px 0 12px;
  font-size: 16px;
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
