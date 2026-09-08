<script setup>
// 课程管理-知识库页（教师，/courses/:id/manage/kb）：
// 给课程内容添加文件能力（方法/工具）：上传讲义/文档入库（Python RAG 异步解析）
// 说明：章节正文会在「保存正文」时自动同步进知识库；本页管理额外上传的文档。
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listCourseKbFiles, uploadCourseKbFile, getKbTask, deleteCourseKbFile } from '../../api/rag'

const route = useRoute()
const courseId = Number(route.params.id)

const kbFiles = ref([])
const kbTotalChunks = ref(0)
const kbLoading = ref(false)
const uploading = ref(false)
const kbError = ref('')
const msg = ref('')

const statusLabel = (s) => {
  const map = {
    done: '已入库',
    processing: '解析中',
    pending: '排队中',
    failed: '解析失败',
    awaiting_decision: '待处理冲突'
  }
  return map[s] || s || '—'
}

const flash = (m, err) => {
  msg.value = err ? `【失败】${m}` : m
  setTimeout(() => (msg.value = ''), 6000)
}

const loadKbFiles = async () => {
  kbLoading.value = true
  kbError.value = ''
  try {
    const data = await listCourseKbFiles(courseId)
    kbFiles.value = Array.isArray(data?.files) ? data.files : []
    kbTotalChunks.value = data?.totalChunks || 0
  } catch (e) {
    kbError.value = '知识库服务暂不可用：' + e.message
    kbFiles.value = []
    kbTotalChunks.value = 0
  } finally {
    kbLoading.value = false
  }
}

const onUploadKbFile = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const data = await uploadCourseKbFile(courseId, file)
    const taskId = data?.task_id
    if (!taskId) throw new Error('未返回任务 id')
    flash(`已提交「${file.name}」入库，解析中…`)
    // 轮询任务直到完成
    for (let i = 0; i < 90; i++) {
      await new Promise((r) => setTimeout(r, 2000))
      const t = await getKbTask(taskId)
      if (t?.status === 'done') {
        flash(`「${file.name}」入库完成：${t.inserted ?? 0} 个片段`)
        break
      }
      if (t?.status === 'failed') {
        flash(`「${file.name}」入库失败：${t?.error || '未知错误'}`, true)
        break
      }
      if (t?.status === 'awaiting_decision') {
        flash(`「${file.name}」存在同名冲突，等待人工决策`, true)
        break
      }
    }
    await loadKbFiles()
  } catch (err) {
    flash('上传失败：' + err.message, true)
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

const onDeleteKbFile = async (f) => {
  if (!confirm(`删除知识文档「${f.filename}」？将清空其全部向量，不可恢复。`)) return
  try {
    await deleteCourseKbFile(courseId, f.doc_id)
    flash('文档已删除')
    await loadKbFiles()
  } catch (e) {
    flash('删除失败：' + e.message, true)
  }
}

onMounted(loadKbFiles)
</script>

<template>
  <div class="page">
    <p v-if="msg" class="msg" :class="{ err: msg.startsWith('【失败】') }">{{ msg }}</p>

    <!-- 总览与上传 -->
    <div class="panel">
      <div class="kb-head">
        <div class="kb-stats">
          <div class="stat">
            <b>{{ kbFiles.length }}</b>
            <span>文档</span>
          </div>
          <div class="stat">
            <b>{{ kbTotalChunks }}</b>
            <span>向量片段</span>
          </div>
        </div>
        <label class="btn btn-primary upload-btn" :class="{ disabled: uploading }">
          {{ uploading ? '上传解析中…' : '+ 上传知识文档' }}
          <input type="file" hidden :disabled="uploading"
                 accept=".md,.txt,.docx,.pptx,.pdf,.png,.jpg,.jpeg,.bmp,.webp" @change="onUploadKbFile" />
        </label>
      </div>
      <p class="tip">
        支持 md / txt / docx / pptx / pdf / 图片，单个文件建议 ≤ 100MB。上传后进入异步解析，完成即可用于课程知识问答与检索。
      </p>
      <p class="tip">
        章节正文（大纲里的 Markdown）会在「保存正文」时自动同步进知识库，不会出现在下方列表；这里只管理额外上传的讲义/课件/参考资料。
      </p>
    </div>

    <!-- 文件列表 -->
    <div class="panel">
      <h3 class="section-title">知识文档（{{ kbFiles.length }}）</h3>
      <p v-if="kbError" class="err">{{ kbError }}</p>
      <p v-if="kbLoading && !kbFiles.length" class="tip">加载中…</p>
      <table v-else-if="kbFiles.length" class="kb-table">
        <thead>
          <tr><th>文件名</th><th>类型</th><th>片段数</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="f in kbFiles" :key="f.doc_id">
            <td class="f-name" :title="f.filename">{{ f.filename }}</td>
            <td>{{ f.doc_type }}</td>
            <td>{{ f.chunk_count }}</td>
            <td>
              <span class="st" :class="f.status === 'done' ? 'st-ok' : 'st-wait'">{{ statusLabel(f.status) }}</span>
            </td>
            <td class="ops">
              <button v-btn-fx class="btn btn-danger" @click="onDeleteKbFile(f)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else-if="!kbLoading" class="empty-tip">暂无上传的知识文档，点上方按钮添加第一份讲义吧</p>
    </div>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.panel { margin-bottom: 0; }
.msg { color: #2f6fed; }
.msg.err { color: #e05b5b; }

.kb-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.kb-stats { display: flex; gap: 16px; }
.stat { display: flex; flex-direction: column; align-items: center; }
.stat b { font-size: 24px; color: #409eff; }
.stat span { color: #888; font-size: 12px; }
.upload-btn { cursor: pointer; white-space: nowrap; padding: 8px 18px; }
.upload-btn.disabled { opacity: 0.6; pointer-events: none; }
.tip { color: #888; font-size: 13px; margin: 4px 0; line-height: 1.7; }
.err { color: #e05b5b; font-size: 13px; }

.section-title { margin: 0 0 12px; }
.kb-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.kb-table th, .kb-table td { border: 1px solid #ebeef5; padding: 8px; text-align: left; }
.kb-table th { background: #fafafa; color: #666; }
.f-name { max-width: 360px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.st { padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.st-ok { background: #f0f9eb; color: #67c23a; }
.st-wait { background: #fdf6ec; color: #e6a23c; }
.ops { display: flex; gap: 6px; }
</style>
