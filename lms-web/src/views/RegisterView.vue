<script setup>
// 注册页：选择学生/教师身份注册，成功后跳登录页
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { register } from '../api/auth'

const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  userType: 1,
  nickname: '',
  phone: ''
})
const errorMsg = ref('')
const loading = ref(false)

// 注册面板容器：入场动画（上浮淡入）
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
  if (form.password !== form.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      userType: form.userType,
      nickname: form.nickname,
      phone: form.phone
    })
    alert('注册成功，请登录')
    router.push('/login')
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
      <h2>注册</h2>
      <div class="form-item">
        <label>身份</label>
        <select v-model="form.userType">
          <option :value="1">学生</option>
          <option :value="2">教师</option>
        </select>
      </div>
      <div class="form-item">
        <label>用户名</label>
        <input v-model="form.username" placeholder="3-50 位字符" />
      </div>
      <div class="form-item">
        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="6-32 位" />
      </div>
      <div class="form-item">
        <label>确认密码</label>
        <input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" />
      </div>
      <div class="form-item">
        <label>昵称（可选）</label>
        <input v-model="form.nickname" placeholder="展示用昵称" />
      </div>
      <div class="form-item">
        <label>手机号（可选）</label>
        <input v-model="form.phone" placeholder="11 位手机号" />
      </div>
      <p v-if="errorMsg" class="tip-error">{{ errorMsg }}</p>
      <button v-btn-fx class="btn btn-primary auth-btn" :disabled="loading" @click="onSubmit">
        {{ loading ? '注册中...' : '注册' }}
      </button>
      <p class="auth-link">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 60px;
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
