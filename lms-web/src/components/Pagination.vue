<script setup>
// 分页组件：对接后端 PageDTO{total,list}，只发事件不请求数据
import { computed } from 'vue'

const props = defineProps({
  total: { type: Number, required: true },
  pageNo: { type: Number, required: true },
  pageSize: { type: Number, default: 10 }
})

const emit = defineEmits(['page-change'])

// 总页数（0 条数据时视为 1 页，避免除零）
const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

// 页码列表：最多展示 5 个，当前页居中
const pages = computed(() => {
  const count = totalPages.value
  let start = Math.max(1, props.pageNo - 2)
  let end = Math.min(count, start + 4)
  start = Math.max(1, end - 4)
  const arr = []
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})

// 跳转指定页（越界忽略，避免重复触发）
const go = (page) => {
  if (page < 1 || page > totalPages.value || page === props.pageNo) return
  emit('page-change', { pageNo: page, pageSize: props.pageSize })
}
</script>

<template>
  <div class="pagination">
    <span class="total-tip">共 {{ total }} 条</span>
    <button class="btn page-btn" :disabled="pageNo <= 1" @click="go(1)">首页</button>
    <button class="btn page-btn" :disabled="pageNo <= 1" @click="go(pageNo - 1)">上一页</button>
    <button
      v-for="p in pages"
      :key="p"
      class="btn page-btn"
      :class="{ active: p === pageNo }"
      @click="go(p)"
    >{{ p }}</button>
    <button class="btn page-btn" :disabled="pageNo >= totalPages" @click="go(pageNo + 1)">下一页</button>
    <button class="btn page-btn" :disabled="pageNo >= totalPages" @click="go(totalPages)">末页</button>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  padding: 16px 0;
}

.total-tip {
  color: #999;
  font-size: 13px;
  margin-right: 8px;
}

.page-btn {
  min-width: 36px;
  text-align: center;
  padding: 4px 8px;
}

.page-btn.active {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
</style>
