<script setup>
import { computed, nextTick, ref } from 'vue'
import { decideHitl, getAgentTaskTree, getHitlDetail, streamAgentChat } from '../api/agent'
import { isTeacher } from '../utils/auth'
import { normalizeHitlStatus } from '../utils/hitl'

const open = ref(false), tab = ref('chat'), input = ref(''), sending = ref(false), listEl = ref(null)
const sessionId = ref(null), tasks = ref([]), taskLoading = ref(false)
let abortController
const agentType = isTeacher() ? 'teacher-agent' : 'student-agent'
const roleLabel = isTeacher() ? '教师 Copilot' : '学习 Copilot'
const messages = ref([{ role: 'assistant', complete: true, stages: [], content: isTeacher()
  ? '可以让我编排课程、题库、学情与知识库工具。涉及高风险写操作时，我会先向你确认。'
  : '我会结合课程、学习进度与知识库回答，也会把复杂任务的执行轨迹展示给你。' }])
const currentTasks = computed(() => tasks.value.map(t => ({ ...t, depth: Math.max(0, Math.min(Number(t.depth || 0), 4)) })))
const scrollBottom = async () => { await nextTick(); if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight }
const toggle = () => { open.value = !open.value; if (open.value) scrollBottom() }
const refreshTasks = async () => {
  if (!sessionId.value) return
  taskLoading.value = true
  try { tasks.value = await getAgentTaskTree(sessionId.value) || [] } finally { taskLoading.value = false }
}
const loadHitl = async (message, requestId) => {
  message.hitl = { requestId, status: 'PENDING' }
  try {
    const detail = await getHitlDetail(requestId)
    message.hitl = { ...detail, requestId, status: normalizeHitlStatus(detail?.status) }
  } catch (e) { message.hitl.error = e.message }
}
const handleEvent = (message, event, data) => {
  if (event === 'stage') message.stages.push({ ...data, state: 'done' })
  if (event === 'meta') { sessionId.value = data.sessionId; message.taskId = data.taskId || null }
  if (event === 'delta') message.content += data.text || ''
  if (event === 'hitl') loadHitl(message, data.requestId)
  if (event === 'error') { message.error = true; message.content = data.message || 'AI 服务暂不可用' }
  if (event === 'done') message.complete = true
  scrollBottom()
}
const send = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', content: text })
  const reply = { role: 'assistant', content: '', stages: [], complete: false, prompt: text }
  messages.value.push(reply); input.value = ''; sending.value = true; abortController = new AbortController(); scrollBottom()
  try {
    await streamAgentChat({ sessionId: sessionId.value, text, agentType }, (event, data) => handleEvent(reply, event, data), abortController.signal)
    reply.complete = true; await refreshTasks()
  } catch (e) {
    if (e.name !== 'AbortError') { reply.error = true; reply.content = e.message || 'AI 服务暂不可用' }
  } finally { sending.value = false; abortController = null; scrollBottom() }
}
const stop = () => abortController?.abort()
const decide = async (message, decision) => {
  if (!message.hitl || message.hitl.busy) return
  message.hitl.busy = true
  try { await decideHitl(message.hitl.requestId, decision); message.hitl.status = decision === 'approve' ? 'APPROVED' : 'REJECTED' }
  catch (e) { message.hitl.error = e.message } finally { message.hitl.busy = false }
}
const taskStatus = s => ['待执行', '执行中', '已完成', '失败', '已取消'][s] || '未知'
const onKeydown = e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }
</script>

<template>
  <button class="copilot-fab" :class="{ active: open }" :aria-label="open ? '关闭 AI Copilot' : '打开 AI Copilot'" @click="toggle">
    <svg v-if="!open" viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="4"/><ellipse cx="16" cy="16" rx="13" ry="6.5"/><ellipse cx="16" cy="16" rx="6.5" ry="13" transform="rotate(42 16 16)"/></svg>
    <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg><span v-if="!open">Copilot</span>
  </button>
  <transition name="copilot">
    <aside v-if="open" class="copilot-panel" aria-label="AI Copilot 工作台">
      <header class="copilot-header">
        <div class="mark"><svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="4"/><ellipse cx="16" cy="16" rx="13" ry="6.5"/><ellipse cx="16" cy="16" rx="6.5" ry="13" transform="rotate(42 16 16)"/></svg></div>
        <div><strong>{{ roleLabel }}</strong><small><i/> AgentScope harness online</small></div>
        <button class="icon-button" aria-label="关闭" @click="toggle">×</button>
      </header>
      <nav class="copilot-tabs" aria-label="Copilot 视图">
        <button :class="{ selected: tab === 'chat' }" @click="tab = 'chat'">对话</button>
        <button :class="{ selected: tab === 'tasks' }" @click="tab = 'tasks'; refreshTasks()">任务树 <span v-if="tasks.length">{{ tasks.length }}</span></button>
      </nav>
      <section v-show="tab === 'chat'" ref="listEl" class="conversation" aria-live="polite">
        <article v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
          <div v-if="message.role === 'assistant'" class="speaker">COPILOT</div>
          <div class="message-body" :class="{ error: message.error }">
            <div v-if="message.stages?.length" class="stage-strip"><span v-for="stage in message.stages" :key="stage.key"><i/>{{ stage.label }}</span></div>
            <p v-if="message.content">{{ message.content }}</p>
            <div v-else-if="!message.complete" class="thinking"><i/><i/><i/><span>执行中</span></div>
            <section v-if="message.hitl" class="hitl-card">
              <div class="hitl-title"><span>需要你的确认</span><code>{{ message.hitl.actionType || '高风险操作' }}</code></div>
              <p>{{ message.hitl.reason || 'Harness 已暂停该动作，批准前不会继续执行。' }}</p>
              <dl><div><dt>动作</dt><dd>{{ message.hitl.actionId || message.hitl.requestId }}</dd></div><div><dt>发起者</dt><dd>{{ message.hitl.inviter || 'personal-agent' }}</dd></div></dl>
              <div v-if="message.hitl.status === 'PENDING'" class="hitl-actions">
                <button class="approve" :disabled="message.hitl.busy" @click="decide(message, 'approve')">批准此次操作</button>
                <button :disabled="message.hitl.busy" @click="decide(message, 'reject')">拒绝</button>
              </div>
              <div v-else-if="message.hitl.status === 'APPROVED'" class="decision approved">已批准；再次发送原指令即可继续</div>
              <div v-else-if="message.hitl.status === 'REJECTED'" class="decision rejected">已拒绝，动作不会执行</div>
              <div v-else class="decision unknown">确认状态未知，请刷新后重试</div>
              <small v-if="message.hitl.error" class="inline-error">{{ message.hitl.error }}</small>
            </section>
          </div>
        </article>
      </section>
      <section v-show="tab === 'tasks'" class="task-board">
        <div class="task-head"><div><strong>会话任务树</strong><small>真实记录邀请、工具与管道节点</small></div><button @click="refreshTasks">刷新</button></div>
        <div v-if="!sessionId" class="empty-state">开始一次对话后，这里会出现可追踪的执行路径。</div>
        <div v-else-if="taskLoading" class="empty-state">正在同步任务状态…</div>
        <div v-else-if="!currentTasks.length" class="empty-state">本会话暂未生成任务节点。</div>
        <ol v-else class="task-list"><li v-for="task in currentTasks" :key="task.taskId" :style="{ '--depth': task.depth }"><span class="task-node" :class="`s${task.status}`"/><div><strong>{{ task.actionRef || task.taskType || task.actionType }}</strong><small>{{ task.agentName }} · {{ taskStatus(task.status) }}</small></div></li></ol>
      </section>
      <footer class="composer">
        <textarea v-model="input" rows="2" :placeholder="isTeacher() ? '描述要生成或分析的教学任务…' : '询问课程、知识点或学习计划…'" :disabled="sending" @keydown="onKeydown"/>
        <button v-if="sending" class="send-button stop" @click="stop" aria-label="停止生成"><span/></button>
        <button v-else class="send-button" :disabled="!input.trim()" @click="send" aria-label="发送"><svg viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6"/></svg></button>
        <small>Enter 发送 · Shift + Enter 换行</small>
      </footer>
    </aside>
  </transition>
</template>

<style scoped>
.copilot-fab{position:fixed;right:24px;bottom:24px;z-index:1001;height:54px;padding:0 17px;border:0;border-radius:18px;display:flex;align-items:center;gap:9px;background:#132238;color:#fff;font:650 13px/1 Inter,"PingFang SC",sans-serif;letter-spacing:.03em;cursor:pointer;box-shadow:0 14px 34px rgba(19,34,56,.26);transition:.25s ease}.copilot-fab:hover{transform:translateY(-2px)}.copilot-fab.active{width:46px;padding:0;justify-content:center;background:#fff;color:#132238;border:1px solid #dfe6ef}.copilot-fab svg,.mark svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.8}.copilot-panel{position:fixed;right:24px;bottom:90px;z-index:1000;width:min(470px,calc(100vw - 32px));height:min(720px,calc(100vh - 116px));display:grid;grid-template-rows:auto auto 1fr auto;background:#f8fafc;color:#132238;border:1px solid rgba(19,34,56,.12);border-radius:22px;overflow:hidden;box-shadow:0 24px 70px rgba(15,30,50,.22);font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif}.copilot-header{display:flex;align-items:center;gap:12px;padding:17px 18px 13px;background:#fff}.mark{width:38px;height:38px;border-radius:12px;display:grid;place-items:center;color:#2563eb;background:#eaf1ff}.copilot-header>div:nth-child(2){display:grid;gap:2px}.copilot-header strong{font-size:15px}.copilot-header small,.task-head small{display:block;color:#758196;font-size:11px}.copilot-header small i{display:inline-block;width:6px;height:6px;margin-right:5px;border-radius:50%;background:#27a36a;box-shadow:0 0 0 3px #dbf4e8}.icon-button{margin-left:auto;border:0;background:transparent;color:#718096;font-size:24px;cursor:pointer}.copilot-tabs{display:flex;gap:4px;padding:0 16px 10px;background:#fff;border-bottom:1px solid #e7ebf1}.copilot-tabs button{border:0;border-radius:8px;padding:7px 12px;background:transparent;color:#667085;font-weight:600;cursor:pointer}.copilot-tabs button.selected{background:#edf3ff;color:#1e5ed8}.copilot-tabs span{margin-left:4px;padding:1px 5px;border-radius:9px;background:#dce8ff;font-size:10px}.conversation,.task-board{overflow-y:auto;padding:18px}.message{margin-bottom:18px}.message.user{display:flex;justify-content:flex-end}.speaker{margin:0 0 5px 4px;color:#8190a5;font-size:9px;font-weight:800;letter-spacing:.14em}.message-body{max-width:88%;padding:12px 14px;border-radius:5px 16px 16px 16px;background:#fff;border:1px solid #e5eaf0;box-shadow:0 4px 15px rgba(27,45,70,.05)}.user .message-body{border:0;border-radius:16px 5px 16px 16px;background:#1f63dc;color:#fff;box-shadow:none}.message-body.error{border-color:#f0c6ca;background:#fff8f8}.message-body p{margin:0;white-space:pre-wrap;word-break:break-word;font-size:14px;line-height:1.7}.stage-strip{display:flex;flex-wrap:wrap;gap:5px;margin-bottom:10px}.stage-strip span{padding:4px 7px;border-radius:6px;background:#f0f4f8;color:#68768a;font-size:10px}.stage-strip i{display:inline-block;width:5px;height:5px;margin-right:4px;border-radius:50%;background:#23a6d5}.thinking{display:flex;align-items:center;gap:4px;height:22px;color:#7c8798;font-size:11px}.thinking i{width:5px;height:5px;border-radius:50%;background:#2563eb;animation:pulse 1s infinite}.thinking i:nth-child(2){animation-delay:.15s}.thinking i:nth-child(3){animation-delay:.3s}.hitl-card{margin-top:12px;padding:13px;border-radius:12px;border:1px solid #f1c578;background:#fffaf0}.hitl-title{display:flex;justify-content:space-between;gap:8px}.hitl-title span{font-weight:700;font-size:13px}.hitl-title code{color:#a35e08;font-size:10px}.hitl-card>p{margin:8px 0;color:#715833;font-size:12px}.hitl-card dl{margin:8px 0}.hitl-card dl div{display:grid;grid-template-columns:55px 1fr;gap:8px;font-size:11px}.hitl-card dt{color:#947850}.hitl-card dd{margin:0;overflow-wrap:anywhere}.hitl-actions{display:flex;gap:7px;margin-top:10px}.hitl-actions button,.task-head button{border:1px solid #d8dee8;border-radius:8px;padding:7px 10px;background:#fff;color:#405069;cursor:pointer;font-weight:600}.hitl-actions .approve{border-color:#2563eb;background:#2563eb;color:#fff}.decision{margin-top:9px;font-size:11px;font-weight:700}.decision.approved{color:#198754}.decision.rejected,.inline-error{color:#c43d4b}.task-board{background:#f7f9fc}.task-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.task-head strong{font-size:15px}.task-head button{padding:5px 9px}.empty-state{padding:38px 24px;border:1px dashed #cfd8e5;border-radius:14px;text-align:center;color:#7a8798;font-size:13px}.task-list{list-style:none;margin:0;padding:0}.task-list li{position:relative;display:flex;align-items:flex-start;gap:11px;margin-left:calc(var(--depth) * 20px);padding:0 0 18px}.task-list li:not(:last-child)::before{content:"";position:absolute;left:5px;top:12px;bottom:0;border-left:1px solid #ccd6e4}.task-node{position:relative;z-index:1;width:11px;height:11px;margin-top:3px;border:2px solid #8c99aa;border-radius:50%;background:#fff;flex:0 0 auto}.task-node.s1{border-color:#2563eb;box-shadow:0 0 0 4px #dce8ff}.task-node.s2{border-color:#27a36a;background:#27a36a}.task-node.s3{border-color:#dc5260;background:#dc5260}.task-list strong{display:block;font-size:12px;overflow-wrap:anywhere}.task-list small{color:#8290a3;font-size:10px}.composer{position:relative;padding:12px 58px 24px 14px;background:#fff;border-top:1px solid #e1e7ef}.composer textarea{width:100%;min-height:48px;max-height:112px;padding:9px 8px;border:0;outline:0;resize:none;color:#17263b;font:13px/1.5 inherit;box-sizing:border-box}.composer small{position:absolute;left:21px;bottom:7px;color:#99a3b1;font-size:9px}.send-button{position:absolute;right:14px;top:16px;width:38px;height:38px;border:0;border-radius:12px;background:#2563eb;color:#fff;display:grid;place-items:center;cursor:pointer}.send-button:disabled{background:#b9c5d6}.send-button svg{width:20px;fill:none;stroke:currentColor;stroke-width:2}.send-button.stop{background:#132238}.send-button.stop span{width:10px;height:10px;border-radius:2px;background:#fff}.copilot-enter-active,.copilot-leave-active{transition:opacity .2s ease,transform .25s cubic-bezier(.2,.8,.2,1)}.copilot-enter-from,.copilot-leave-to{opacity:0;transform:translateY(14px) scale(.98)}@keyframes pulse{0%,70%,100%{opacity:.25;transform:translateY(0)}35%{opacity:1;transform:translateY(-3px)}}@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}@media(max-width:600px){.copilot-fab{right:14px;bottom:14px}.copilot-panel{inset:0;width:100vw;height:100dvh;border:0;border-radius:0}.copilot-header{padding-top:max(16px,env(safe-area-inset-top))}.message-body{max-width:94%}.composer{padding-bottom:max(25px,env(safe-area-inset-bottom))}}
</style>
