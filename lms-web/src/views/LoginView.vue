<script setup>
// 登录页：账号密码登录，成功后写入登录态并按 redirect 回跳
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import gsap from 'gsap'
import { login } from '../api/auth'
import { setAuth } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const form = reactive({ username: '', password: '' })
const errorMsg = ref('')
const loading = ref(false)

// 登录面板容器：入场动画（上浮淡入）
const panelEl = ref(null)
let gsapCtx = null

onMounted(() => {
  gsapCtx = gsap.context(() => {
    gsap.fromTo(panelEl.value,
      { y: 24, opacity: 0 },
      { y: 0, opacity: 1, duration: 0.5, ease: 'power2.out' })
  }, panelEl.value)
})

onUnmounted(() => {
  if (gsapCtx) gsapCtx.revert()
})

const onSubmit = async () => {
  errorMsg.value = ''
  if (!form.username || !form.password) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const data = await login(form)
    setAuth(data)
    router.push(String(route.query.redirect || '/courses'))
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div ref="panelEl" class="auth-panel">
      <h2>登录</h2>
      <div class="form-item">
        <label>用户名</label>
        <input v-model="form.username" placeholder="请输入用户名" @keyup.enter="onSubmit" />
      </div>
      <div class="form-item">
        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="请输入密码" @keyup.enter="onSubmit" />
      </div>
      <p v-if="errorMsg" class="tip-error">{{ errorMsg }}</p>
      <button v-btn-fx class="btn btn-primary auth-btn" :disabled="loading" @click="onSubmit">
        {{ loading ? '登录中...' : '登录' }}
      </button>
      <p class="auth-link">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}

.auth-panel {
  width: 360px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 28px;
}

.auth-panel h2 {
  text-align: center;
  margin-bottom: 20px;
}

.auth-btn {
  width: 100%;
  margin-top: 6px;
}

.auth-link {
  text-align: center;
  margin-top: 14px;
  font-size: 13px;
  color: #999;
}
</style>
