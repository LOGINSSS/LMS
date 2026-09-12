<script setup>
// 顶栏用户菜单：点头像/昵称弹出下拉（完善个人资料 / 消息通知 / 退出登录）
// 个人资料完善内嵌弹窗：按角色展示扩展字段（学生：学号/专业/年级/班级；教师：院系/职称/简介）
// 消息通知 = 站内信箱（后端落库，双端通用）：
//   - 教师：学生提问 → 「有新问题待回答」，跳课程问答中心处理；
//   - 学生：教师回答 → 「提问已被老师回答」，跳课程问答中心查看。
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { getUserMe, updateUserMe } from '../api/user'
import { logout } from '../api/auth'
import { getUsername, setUsername, clearAuth, isTeacher, isStudent } from '../utils/auth'
import {
  listMyNotifications,
  unreadNotifyCount,
  markNotifyRead,
  markAllNotifyRead
} from '../api/learn'
import { notificationPresentation, questionCenterTarget } from '../utils/courseQa'

const router = useRouter()

const open = ref(false)
const wrapEl = ref(null)
const roleName = isTeacher() ? '教师' : isStudent() ? '学生' : ''

// 名称展示：优先登录态缓存；资料完善后同步刷新
const name = ref(getUsername() || '')
const initial = computed(() => (name.value || 'L').charAt(0))

// 下拉面板：menu 主菜单 / notice 信箱
const panel = ref('menu')

// ---------- 信箱（消息通知，后端落库，未读数做红点） ----------
const inbox = ref([])
const unread = ref(0)
const inboxLoading = ref(false)
const inboxTip = ref('')

const refreshUnread = async () => {
  try {
    unread.value = Number(await unreadNotifyCount()) || 0
  } catch (e) {
    // 未读数拉取失败不阻塞
  }
}

const loadInbox = async () => {
  inboxLoading.value = true
  inboxTip.value = ''
  try {
    const data = await listMyNotifications({ pageNo: 1, pageSize: 50 })
    inbox.value = Array.isArray(data?.list) ? data.list : []
    if (!inbox.value.length) inboxTip.value = '暂无消息'
  } catch (e) {
    inboxTip.value = '消息加载失败'
    inbox.value = []
  } finally {
    inboxLoading.value = false
    refreshUnread()
  }
}

const openNotices = () => {
  panel.value = 'notice'
  loadInbox()
}

const backToMenu = () => {
  panel.value = 'menu'
}

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')
const noticeMeta = (notification) => notificationPresentation(notification)

// 全部已读
const markAll = async () => {
  try {
    await markAllNotifyRead()
    inbox.value.forEach((n) => { n.isRead = 1 })
    unread.value = 0
  } catch (e) {
    // 忽略
  }
}

// 单条标记已读（best-effort）
const markOne = async (n) => {
  if (n.isRead === 1) return
  n.isRead = 1
  unread.value = Math.max(0, unread.value - 1)
  try {
    await markNotifyRead([n.id])
  } catch (e) {
    refreshUnread()
  }
}

// 点击消息：标记已读并跳到课程问答中心的具体问题
const jumpNotify = async (n) => {
  await markOne(n)
  open.value = false
  panel.value = 'menu'
  router.push(questionCenterTarget(n.courseId, n.questionId))
}

// 资料完善弹窗 ----------
const showEdit = ref(false)
const loadingEdit = ref(false)
const saving = ref(false)
const editMsg = ref('')

const form = reactive({
  nickname: '',
  phone: '',
  email: '',
  college: '',
  title: '',
  bio: '',
  studentNo: '',
  major: '',
  grade: '',
  className: ''
})

const openEditor = async () => {
  open.value = false
  showEdit.value = true
  editMsg.value = ''
  loadingEdit.value = true
  try {
    const p = await getUserMe()
    Object.assign(form, {
      nickname: p?.nickname || '',
      phone: p?.phone || '',
      email: p?.email || '',
      college: p?.college || '',
      title: p?.title || '',
      bio: p?.bio || '',
      studentNo: p?.studentNo || '',
      major: p?.major || '',
      grade: p?.grade || '',
      className: p?.className || ''
    })
  } catch (e) {
    editMsg.value = '资料加载失败：' + (e.message || e)
  } finally {
    loadingEdit.value = false
  }
}

const saveProfile = async () => {
  if (!form.nickname.trim()) {
    editMsg.value = '昵称不能为空'
    return
  }
  saving.value = true
  editMsg.value = ''
  try {
    // 后端仅更新非 null 字段：空白字段不提交，避免误清空原值
    const payload = { nickname: form.nickname.trim() }
    for (const key of ['phone', 'email', 'college', 'title', 'bio', 'studentNo', 'major', 'grade', 'className']) {
      const v = String(form[key] ?? '').trim()
      if (v) payload[key] = v
    }
    await updateUserMe(payload)
    setUsername(form.nickname.trim())
    name.value = form.nickname.trim()
    showEdit.value = false
    alert('个人资料已保存')
  } catch (e) {
    editMsg.value = '保存失败：' + (e.message || e)
  } finally {
    saving.value = false
  }
}

// ---------- 退出登录 ----------
const handleLogout = async () => {
  try {
    await logout()
  } catch (e) {
    // 登出接口失败不阻塞本地登出
  }
  clearAuth()
  router.push('/login')
}

// ---------- 点击外部关闭下拉 ----------
const onDocClick = (e) => {
  if (wrapEl.value && !wrapEl.value.contains(e.target)) open.value = false
}
const onKey = (e) => {
  if (e.key === 'Escape') {
    open.value = false
    showEdit.value = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onDocClick)
  document.addEventListener('keydown', onKey)
  // 未读数（红点）挂载即拉取，双端通用
  refreshUnread()
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div ref="wrapEl" class="user-menu">
    <button v-btn-fx class="user-trigger" @click="open = !open">
      <span class="avatar">{{ initial }}</span>
      <span class="uname">{{ name }}</span>
      <span class="caret" :class="{ up: open }">▾</span>
    </button>

    <transition name="drop">
      <div v-if="open" class="dropdown" :class="{ wide: panel === 'notice' }">
        <!-- 主菜单 -->
        <template v-if="panel === 'menu'">
          <div class="drop-head">
            <div class="drop-name">{{ name }}</div>
            <span class="role-tag">{{ roleName }}</span>
          </div>
          <button v-btn-fx class="drop-item" @click="openEditor">
            <span>完善个人资料</span>
            <span class="arrow">›</span>
          </button>
          <button v-btn-fx class="drop-item" @click="openNotices">
            <span>消息通知</span>
            <span class="badge" v-if="unread > 0">{{ unread > 99 ? '99+' : unread }}</span>
          </button>
          <div class="drop-sep"></div>
          <button v-btn-fx class="drop-item danger" @click="handleLogout">
            <span>退出登录</span>
          </button>
        </template>

        <!-- 消息通知（信箱）面板 -->
        <template v-else>
          <div class="notice-head">
            <button v-btn-fx class="back" @click="backToMenu">‹</button>
            <span class="nt-title">消息通知</span>
            <button v-btn-fx class="refresh" title="全部已读" @click="markAll">全部已读</button>
            <button v-btn-fx class="refresh" title="刷新" @click="loadInbox">↻</button>
          </div>
          <p v-if="inboxLoading && !inbox.length" class="nt-tip">加载中…</p>
          <p v-else-if="inboxTip && !inbox.length" class="nt-empty">{{ inboxTip }}</p>
          <div v-else class="notice-list">
            <div v-for="n in inbox" :key="n.id" class="notice-row" :class="{ unread: n.isRead !== 1 }">
              <button v-btn-fx class="row-main" @click="jumpNotify(n)">
                <span class="dot" v-if="n.isRead !== 1"></span>
                <span class="chip" :class="`chip-${noticeMeta(n).tone}`">
                  {{ noticeMeta(n).label }}
                </span>
                <span class="nt-body">
                  <span class="nt-text">{{ n.title || '（无标题）' }}</span>
                  <span v-if="n.content" class="nt-sub">{{ n.content }}</span>
                  <span class="nt-time">{{ fmtTime(n.createTime) }}</span>
                </span>
                <span class="notice-action">{{ noticeMeta(n).action }}</span>
                <span class="arrow">›</span>
              </button>
            </div>
          </div>
        </template>
      </div>
    </transition>

    <!-- 资料完善弹窗 -->
    <div v-if="showEdit" class="modal-mask" @click.self="showEdit = false">
      <div class="modal">
        <div class="modal-head">
          <h3>完善个人资料</h3>
          <button v-btn-fx class="close" @click="showEdit = false">✕</button>
        </div>
        <p v-if="editMsg" class="modal-msg">{{ editMsg }}</p>
        <div v-if="loadingEdit" class="empty-tip">加载中…</div>
        <div v-else class="modal-body">
          <div class="form-item">
            <label>昵称</label>
            <input v-model="form.nickname" placeholder="必填" />
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>手机号</label>
              <input v-model="form.phone" placeholder="选填" />
            </div>
            <div class="form-item">
              <label>邮箱</label>
              <input v-model="form.email" placeholder="选填" />
            </div>
          </div>

          <template v-if="isTeacher()">
            <div class="form-row">
              <div class="form-item">
                <label>院系</label>
                <input v-model="form.college" placeholder="选填" />
              </div>
              <div class="form-item">
                <label>职称</label>
                <input v-model="form.title" placeholder="选填" />
              </div>
            </div>
            <div class="form-item">
              <label>个人简介</label>
              <textarea v-model="form.bio" rows="3" placeholder="让同学更了解你（选填）"></textarea>
            </div>
          </template>

          <template v-else-if="isStudent()">
            <div class="form-row">
              <div class="form-item">
                <label>学号</label>
                <input v-model="form.studentNo" placeholder="选填" />
              </div>
              <div class="form-item">
                <label>专业</label>
                <input v-model="form.major" placeholder="选填" />
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label>年级</label>
                <input v-model="form.grade" placeholder="选填" />
              </div>
              <div class="form-item">
                <label>班级</label>
                <input v-model="form.className" placeholder="选填" />
              </div>
            </div>
          </template>
        </div>
        <div class="modal-foot">
          <button v-btn-fx class="btn" @click="showEdit = false">取消</button>
          <button v-btn-fx class="btn btn-primary" :disabled="saving || loadingEdit" @click="saveProfile">
            {{ saving ? '保存中…' : '保存资料' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-menu { position: relative; }

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 20px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #333;
}

.user-trigger:hover {
  border-color: #409eff;
}

.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #7d5fff);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.uname {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret { color: #999; font-size: 12px; }
.caret.up { transform: rotate(180deg); }

.dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: 220px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);
  padding: 6px;
  z-index: 30;
}

.dropdown.wide {
  width: min(390px, calc(100vw - 24px));
}

.drop-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 4px;
}

.drop-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tag {
  padding: 1px 8px;
  border-radius: 10px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  flex-shrink: 0;
}

.drop-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  text-align: left;
}

.drop-item:hover { background: #f5f7fa; color: #409eff; }
.drop-item.danger:hover { color: #f56c6c; }
.drop-sep { height: 1px; background: #f0f2f5; margin: 4px 0; }
.arrow { color: #c0c4cc; flex-shrink: 0; }

.badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: auto;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

/* 消息通知面板 */
.notice-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px 12px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 4px;
}

.back, .refresh {
  border: none;
  background: transparent;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  padding: 0 4px;
  white-space: nowrap;
}

.back { font-size: 15px; }
.back:hover, .refresh:hover { color: #409eff; }

.nt-title { flex: 1; font-size: 16px; font-weight: 600; color: #2d3e50; }

.nt-tip, .nt-empty {
  padding: 18px 10px;
  text-align: center;
  color: #999;
  font-size: 13px;
}

.notice-list {
  max-height: 420px;
  overflow: auto;
}

.notice-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  padding: 5px;
  border-radius: 8px;
}

.notice-row:hover { background: #f5f7fa; }

.notice-row.unread { background: #f0f7ff; }
.notice-row.unread:hover { background: #e6f0ff; }

.row-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
  min-width: 0;
  padding: 10px 6px 10px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f56c6c;
  margin-top: 7px;
  flex-shrink: 0;
}

.chip {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  color: #fff;
  margin-top: 2px;
}

.chip-pending { background: #e05b5b; }
.chip-answered { background: #2f9e6e; }
.chip-resolved { background: #64748b; }

.nt-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.nt-text {
  font-size: 14px;
  color: #333;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.nt-sub {
  font-size: 13px;
  color: #888;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.nt-time { font-size: 12px; color: #9ba8b5; }

.row-main .arrow { margin-top: 6px; }
.notice-row:hover .arrow { color: #409eff; }

.notice-action {
  align-self: center;
  flex-shrink: 0;
  color: #337fbd;
  font-size: 12px;
}

/* 下拉过渡：只动 opacity/transform */
.drop-enter-active, .drop-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.drop-enter-from, .drop-leave-to { opacity: 0; transform: translateY(-4px); }

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  width: min(520px, calc(100vw - 32px));
  max-height: 86vh;
  overflow: auto;
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18);
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.modal-head h3 { font-size: 17px; }

.close {
  border: none;
  background: transparent;
  font-size: 15px;
  color: #999;
  cursor: pointer;
}

.modal-msg {
  color: #f56c6c;
  font-size: 13px;
  margin-bottom: 8px;
}

.form-row { display: flex; gap: 12px; }
.form-row .form-item { flex: 1; }

.modal-body textarea { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 14px; font-family: inherit; }
.modal-body textarea:focus { outline: none; border-color: #409eff; }

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}
</style>
