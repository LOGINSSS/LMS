<script setup>
// 顶栏用户菜单：点头像/昵称弹出下拉（完善个人资料 / 消息通知 / 退出登录）
// 个人资料完善内嵌弹窗：按角色展示扩展字段（学生：学号/专业/年级/班级；教师：院系/职称/简介）
// 消息通知：教师 = 自己课程里「待回答的学生问题」实时聚合提醒（点击箭头跳课程详情待回答区）
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { getUserMe, updateUserMe } from '../api/user'
import { logout } from '../api/auth'
import { getUsername, setUsername, clearAuth, isTeacher, isStudent } from '../utils/auth'
import { queryMyCourses } from '../api/course'
import { listQuestions } from '../api/learn'

const router = useRouter()

const open = ref(false)
const wrapEl = ref(null)
const teacherRole = isTeacher()
const roleName = teacherRole ? '教师' : isStudent() ? '学生' : ''

// 名称展示：优先登录态缓存；资料完善后同步刷新
const name = ref(getUsername() || '')
const initial = computed(() => (name.value || 'L').charAt(0))

// 下拉面板：menu 主菜单 / notice 消息通知
const panel = ref('menu')

// ---------- 消息通知（教师：待回答的学生问题提醒） ----------
const notices = ref([])
const noticesLoading = ref(false)
const noticeTip = ref('')

// 待回答问题总数（用于主菜单上的红点/数字）
const pendingTotal = computed(() => notices.value.reduce((s, n) => s + (n.count || 0), 0))

const loadNotices = async () => {
  if (!teacherRole) {
    notices.value = []
    return
  }
  noticesLoading.value = true
  noticeTip.value = ''
  try {
    const data = await queryMyCourses({ pageNo: 1, pageSize: 500 })
    const courses = (data?.list || []).slice(0, 20)
    const rows = await Promise.all(courses.map(async (c) => {
      try {
        const q = await listQuestions({ courseId: c.id, pageNo: 1, pageSize: 100 })
        const qs = Array.isArray(q?.list) ? q.list : []
        // 待回答 = 还没有任何回答的问题
        const pend = qs.filter((x) => !(x.answers && x.answers.length))
        if (!pend.length) return null
        return {
          courseId: c.id,
          courseName: c.name,
          count: pend.length,
          latestAt: pend[0]?.createTime || ''
        }
      } catch (e) {
        return null
      }
    }))
    notices.value = rows
      .filter(Boolean)
      .sort((a, b) => String(b.latestAt).localeCompare(String(a.latestAt)))
  } catch (e) {
    noticeTip.value = '通知加载失败'
    notices.value = []
  } finally {
    noticesLoading.value = false
  }
}

const openNotices = () => {
  panel.value = 'notice'
  if (teacherRole) loadNotices()
}

const goNotice = (n) => {
  open.value = false
  panel.value = 'menu'
  router.push({ path: `/courses/${n.courseId}`, hash: '#qa-pending' })
}

const backToMenu = () => {
  panel.value = 'menu'
}

// ---------- 资料完善弹窗 ----------
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
  if (teacherRole) loadNotices()
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
            <span class="badge" v-if="pendingTotal > 0">{{ pendingTotal }}</span>
          </button>
          <div class="drop-sep"></div>
          <button v-btn-fx class="drop-item danger" @click="handleLogout">
            <span>退出登录</span>
          </button>
        </template>

        <!-- 消息通知面板 -->
        <template v-else>
          <div class="notice-head">
            <button v-btn-fx class="back" @click="backToMenu">‹</button>
            <span class="nt-title">消息通知</span>
            <button v-btn-fx v-if="teacherRole" class="refresh" title="刷新" @click="loadNotices">↻</button>
          </div>
          <p v-if="noticeTip" class="nt-tip">{{ noticeTip }}</p>
          <p v-if="noticesLoading && !notices.length" class="nt-tip">加载中…</p>
          <div v-else-if="!notices.length" class="nt-empty">暂无消息通知</div>
          <div v-else class="notice-list">
            <button v-for="n in notices" :key="n.courseId" v-btn-fx class="notice-row" @click="goNotice(n)">
              <span class="nt-text">
                《{{ n.courseName }}》有 {{ n.count }} 个新问题需要回答，快去看看吧~
              </span>
              <span class="arrow">›</span>
            </button>
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
  width: 300px;
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
  gap: 6px;
  padding: 6px 8px 8px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 4px;
}

.back, .refresh {
  border: none;
  background: transparent;
  font-size: 15px;
  color: #666;
  cursor: pointer;
  padding: 0 4px;
}

.back:hover, .refresh:hover { color: #409eff; }

.nt-title { flex: 1; font-size: 14px; font-weight: 600; color: #333; }

.nt-tip, .nt-empty {
  padding: 18px 10px;
  text-align: center;
  color: #999;
  font-size: 13px;
}

.notice-list {
  max-height: 280px;
  overflow: auto;
}

.notice-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.notice-row:hover { background: #f5f7fa; }

.notice-row:hover .arrow { color: #409eff; }

.nt-text {
  flex: 1;
  font-size: 13px;
  color: #333;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
