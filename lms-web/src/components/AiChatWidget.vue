<script setup>
// 全局 AI 聊天浮窗（0.2 前端 AI 入口）
// 右下角悬浮按钮 → 抽屉聊天面板，接 /agent/chat（student/teacher 双角色）
import { ref, nextTick } from 'vue'
import { agentChat } from '../api/agent'
import { isTeacher } from '../utils/auth'

const open = ref(false)
const messages = ref([])
const input = ref('')
const sending = ref(false)
const listEl = ref(null)

const agentType = isTeacher() ? 'teacher' : 'student'

const scrollBottom = async () => {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

const toggle = () => {
  open.value = !open.value
  if (open.value) {
    messages.value = [
      { role: 'assistant', content: '你好！我是你的 AI 学习助手。可以问我课程、学习进度、积分等问题。' }
    ]
  }
}

const send = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  sending.value = true
  scrollBottom()
  try {
    const res = await agentChat({ text, agentType })
    const answer = typeof res === 'string' ? res : (res?.content || res?.text || JSON.stringify(res))
    messages.value.push({ role: 'assistant', content: answer })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: `AI 服务暂不可用：${e.message}` })
  } finally {
    sending.value = false
    scrollBottom()
  }
}
</script>

<template>
  <!-- 悬浮按钮 -->
  <button v-btn-fx class="ai-fab" @click="toggle">
    {{ open ? '✕' : '🤖 AI' }}
  </button>

  <!-- 聊天抽屉 -->
  <transition name="chat">
    <div v-if="open" class="ai-panel">
      <div class="ai-header">AI 学习助手</div>
      <div ref="listEl" class="ai-list">
        <div v-for="(m, i) in messages" :key="i" class="ai-msg" :class="m.role">
          <div class="bubble">{{ m.content }}</div>
        </div>
        <div v-if="sending" class="ai-msg assistant">
          <div class="bubble">思考中...</div>
        </div>
      </div>
      <div class="ai-input">
        <input v-model="input" placeholder="问我课程、学习、积分…" @keyup.enter="send" />
        <button v-btn-fx class="btn btn-primary" @click="send" :disabled="sending">发送</button>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  border: none;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
}

.ai-panel {
  position: fixed;
  right: 24px;
  bottom: 92px;
  z-index: 999;
  width: 360px;
  height: 480px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-header {
  padding: 12px 16px;
  background: #409eff;
  color: #fff;
  font-weight: 600;
}

.ai-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-msg {
  display: flex;
}

.ai-msg.user {
  justify-content: flex-end;
}

.ai-msg.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.ai-msg.user .bubble {
  background: #409eff;
  color: #fff;
}

.ai-msg.assistant .bubble {
  background: #f0f2f5;
  color: #333;
}

.ai-input {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
}

.ai-input input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}

.chat-enter-active,
.chat-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.chat-enter-from,
.chat-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
