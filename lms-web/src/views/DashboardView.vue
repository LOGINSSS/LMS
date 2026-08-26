<script setup>
// 数据看板页：总览指标、今日数据、热门课程 Top、积分榜 Top
import { ref, onMounted } from 'vue'
import { getDashboard, getTodayStats, getTopCourses, getTopPoints } from '../api/statistics'

const overview = ref(null)
const today = ref(null)
const topCourses = ref([])
const topPoints = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [ov, td, tc, tp] = await Promise.all([
      getDashboard(),
      getTodayStats(),
      getTopCourses(5),
      getTopPoints(5)
    ])
    overview.value = ov
    today.value = td
    topCourses.value = tc
    topPoints.value = tp
  } catch (e) {
    alert(e.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-if="loading" class="empty-tip">加载中...</div>
  <div v-else>
    <h2 class="page-title">数据看板</h2>

    <h3 class="section-title">总览</h3>
    <div class="stat-grid">
      <div class="stat-card">
        <p class="num">{{ overview?.userTotal ?? 0 }}</p>
        <p class="label">用户总数</p>
      </div>
      <div class="stat-card">
        <p class="num">{{ overview?.courseTotal ?? 0 }}</p>
        <p class="label">课程总数</p>
      </div>
      <div class="stat-card">
        <p class="num">{{ overview?.enrollTotal ?? 0 }}</p>
        <p class="label">选课人次</p>
      </div>
      <div class="stat-card">
        <p class="num">{{ overview?.learnTotal ?? 0 }}</p>
        <p class="label">学习人次</p>
      </div>
    </div>

    <h3 class="section-title">今日数据</h3>
    <div class="stat-grid">
      <div class="stat-card">
        <p class="num">{{ today?.todayUser ?? 0 }}</p>
        <p class="label">今日新增用户</p>
      </div>
      <div class="stat-card">
        <p class="num">{{ today?.todayCourse ?? 0 }}</p>
        <p class="label">今日新增课程</p>
      </div>
      <div class="stat-card">
        <p class="num">{{ today?.todaySign ?? 0 }}</p>
        <p class="label">今日签到</p>
      </div>
      <div class="stat-card">
        <p class="num">{{ today?.todayLearn ?? 0 }}</p>
        <p class="label">今日学习人次</p>
      </div>
    </div>

    <div class="two-col">
      <div class="panel">
        <h3 class="section-title">热门课程 Top</h3>
        <ul class="rank-list">
          <li v-for="(c, i) in topCourses" :key="c.courseId">
            <span class="rank">{{ i + 1 }}</span>
            <span class="name">{{ c.name }}</span>
            <span class="count">{{ c.enrollCount }} 人选课</span>
          </li>
          <li v-if="topCourses.length === 0" class="empty-rank">暂无数据</li>
        </ul>
      </div>
      <div class="panel">
        <h3 class="section-title">积分榜 Top</h3>
        <ul class="rank-list">
          <li v-for="(p, i) in topPoints" :key="p.userId">
            <span class="rank">{{ i + 1 }}</span>
            <span class="name">用户 #{{ p.userId }}</span>
            <span class="count">{{ p.totalPoints }} 分</span>
          </li>
          <li v-if="topPoints.length === 0" class="empty-rank">暂无数据</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin-bottom: 16px;
  font-size: 18px;
}

.section-title {
  margin: 18px 0 12px;
  font-size: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.stat-card .num {
  font-size: 26px;
  font-weight: 600;
  color: #409eff;
}

.stat-card .label {
  color: #666;
  font-size: 13px;
  margin-top: 6px;
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

.rank-list .name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-list .count {
  color: #999;
  font-size: 13px;
}

.empty-rank {
  color: #999;
}
</style>
