<script setup>
// 日历 / 课表页（完整版）：复用 CalendarBoard 组件，支持月视图与周课表
import CalendarBoard from '../components/CalendarBoard.vue'
</script>

<template>
  <div class="page">
    <div class="head">
      <h2>日历 / 课表</h2>
      <router-link class="btn" to="/home">返回首页（首页已含日历）</router-link>
    </div>
    <div class="panel">
      <CalendarBoard />
    </div>
  </div>
</template>

<style scoped>
.page { padding: 16px; max-width: 1080px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
</style>
