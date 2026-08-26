<script setup>
// 课程卡片组件：纯展示（封面/标题/简介/分类/价格/教师/选课人数/时间），操作按钮由页面提供
defineProps({
  course: { type: Object, required: true }
})

// 价格展示：0 显示免费
const priceText = (price) => (Number(price) > 0 ? `¥${Number(price).toFixed(2)}` : '免费')
</script>

<template>
  <div class="course-card">
    <div class="cover">
      <img v-if="course.cover" :src="course.cover" alt="课程封面" />
      <span v-else class="cover-placeholder">暂无封面</span>
    </div>
    <div class="body">
      <h3 class="name">{{ course.name }}</h3>
      <p class="intro">{{ course.intro || '暂无简介' }}</p>
      <div class="meta">
        <span v-if="course.category" class="tag">{{ course.category }}</span>
        <span class="price" :class="{ free: Number(course.price) === 0 }">{{ priceText(course.price) }}</span>
      </div>
      <div class="footer">
        <span class="teacher">{{ course.teacherName }}</span>
        <span class="count">{{ course.totalCount ?? 0 }} 人选课</span>
      </div>
      <div class="ops">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.course-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.2s;
}

.course-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.cover {
  height: 140px;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  color: #a0cfff;
  font-size: 14px;
}

.body {
  padding: 12px 14px;
}

.name {
  font-size: 16px;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.intro {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
}

.tag {
  padding: 1px 8px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #666;
  font-size: 12px;
}

.price {
  color: #f56c6c;
  font-weight: 600;
  font-size: 15px;
}

.price.free {
  color: #67c23a;
}

.footer {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #999;
  padding-bottom: 10px;
}

.ops {
  display: flex;
  gap: 8px;
  border-top: 1px solid #f0f0f0;
  padding-top: 10px;
}
</style>
