<script setup>
// 课程管理-题库页（教师，/courses/:id/manage/questions）：本课程题目增删查、题型筛选
// 题目与课程通过绑定关系（bizType=1 课程）关联：新建题目后自动绑定到本课程
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { addQuestion, deleteQuestion, queryQuestionsByBiz, bindQuestionBiz } from '../../api/exam'
import Pagination from '../../components/Pagination.vue'

const route = useRoute()
const courseId = Number(route.params.id)

const all = ref([])          // 本课程全部绑定题目
const typeFilter = ref('')   // '' 全部
const pageNo = ref(1)
const pageSize = 10

const typeLabel = (t) => ({ 1: '单选', 2: '多选', 3: '判断' }[t] || t)
const diffLabel = (d) => ({ 1: '简单', 2: '中等', 3: '困难' }[d] || d)

const filtered = computed(() => {
  const list = typeFilter.value ? all.value.filter((q) => String(q.type) === String(typeFilter.value)) : all.value
  return list
})
const total = computed(() => filtered.value.length)
const pageList = computed(() => filtered.value.slice((pageNo.value - 1) * pageSize, pageNo.value * pageSize))

const load = async () => {
  try {
    const data = await queryQuestionsByBiz(1, courseId)
    all.value = Array.isArray(data) ? data : []
  } catch (e) {
    alert(e.message || '题库加载失败')
    all.value = []
  }
}

const onFilter = () => {
  pageNo.value = 1
}

const onPageChange = ({ pageNo: p }) => {
  pageNo.value = p
}

// ---------- 新建题目（创建后自动绑定本课程） ----------
const showForm = ref(false)
const form = reactive({
  name: '',
  type: 1,
  category: '',
  difficulty: 1,
  analysis: '',
  answer: ''
})
const creating = ref(false)

const onSubmit = async () => {
  if (!form.name.trim()) {
    alert('请填写题干')
    return
  }
  creating.value = true
  try {
    const id = await addQuestion({
      name: form.name.trim(),
      type: Number(form.type),
      category: form.category,
      difficulty: Number(form.difficulty),
      analysis: form.analysis,
      answer: form.answer
    })
    // 自动绑定到本课程题库（bizType=1 课程）
    await bindQuestionBiz(id, 1, courseId, 0)
    alert('创建成功，已加入本课程题库')
    Object.assign(form, { name: '', category: '', analysis: '', answer: '' })
    showForm.value = false
    await load()
  } catch (e) {
    alert(e.message)
  } finally {
    creating.value = false
  }
}

// ---------- 删除题目（逻辑删除，从所有课程题库移除） ----------
const onDelete = async (q) => {
  if (!confirm(`确定删除题目「${q.name}」？题目将从本课程及其他引用处逻辑删除，不可恢复。`)) return
  try {
    await deleteQuestion(q.id)
    await load()
  } catch (e) {
    alert(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <div class="filters">
        <select v-model="typeFilter" @change="onFilter">
          <option value="">全部题型</option>
          <option value="1">单选</option>
          <option value="2">多选</option>
          <option value="3">判断</option>
        </select>
        <span class="count">共 {{ total }} 题</span>
      </div>
      <button v-btn-fx class="btn btn-primary" @click="showForm = !showForm">
        {{ showForm ? '收起' : '新建题目（自动加入本课程）' }}
      </button>
    </div>

    <div v-if="showForm" class="add-form">
      <div class="form-item">
        <label>题干</label>
        <input v-model="form.name" placeholder="必填" />
      </div>
      <div class="form-row">
        <div class="form-item">
          <label>题型</label>
          <select v-model="form.type">
            <option :value="1">单选</option>
            <option :value="2">多选</option>
            <option :value="3">判断</option>
          </select>
        </div>
        <div class="form-item">
          <label>难度</label>
          <select v-model="form.difficulty">
            <option :value="1">简单</option>
            <option :value="2">中等</option>
            <option :value="3">困难</option>
          </select>
        </div>
        <div class="form-item">
          <label>分类</label>
          <input v-model="form.category" placeholder="如 章节一" />
        </div>
      </div>
      <div class="form-item">
        <label>答案（JSON，如 {"option":"A"}）</label>
        <input v-model="form.answer" placeholder='单选 {"option":"A"} / 多选 {"options":["A","B"]} / 判断 {"judge":true}' />
      </div>
      <div class="form-item">
        <label>解析</label>
        <textarea v-model="form.analysis" rows="2" placeholder="可选"></textarea>
      </div>
      <button v-btn-fx class="btn btn-primary" :disabled="creating" @click="onSubmit">
        {{ creating ? '创建中…' : '创建题目' }}
      </button>
    </div>

    <table class="q-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>题干</th>
          <th>题型</th>
          <th>分类</th>
          <th>难度</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="q in pageList" :key="q.id">
          <td>{{ q.id }}</td>
          <td class="q-name">{{ q.name }}</td>
          <td>{{ typeLabel(q.type) }}</td>
          <td>{{ q.category || '-' }}</td>
          <td>{{ diffLabel(q.difficulty) }}</td>
          <td class="ops">
            <button v-btn-fx class="btn btn-danger" @click="onDelete(q)">删除</button>
          </td>
        </tr>
        <tr v-if="!pageList.length">
          <td colspan="6" class="empty-tip">本课程暂无题目，点右上角新建</td>
        </tr>
      </tbody>
    </table>
    <Pagination v-if="total > pageSize" :total="total" :page-no="pageNo" :page-size="pageSize" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 10px;
  flex-wrap: wrap;
}
.filters { display: flex; align-items: center; gap: 10px; }
.count { color: #999; font-size: 13px; }
.add-form {
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.form-row { display: flex; gap: 12px; }
.form-row .form-item { flex: 1; }
textarea { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; border-radius: 4px; font-family: inherit; font-size: 14px; }
.q-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.q-table th, .q-table td { border-bottom: 1px solid #f0f0f0; padding: 10px 8px; text-align: left; }
.q-table th { color: #666; background: #fafafa; }
.q-name { max-width: 320px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ops { display: flex; gap: 6px; }
</style>
