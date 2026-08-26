<script setup>
// 题库管理页（教师）：题目增删查、题型筛选、绑定业务
import { ref, reactive, onMounted } from 'vue'
import { addQuestion, deleteQuestion, queryQuestionPage, bindQuestionBiz } from '../api/exam'
import Pagination from '../components/Pagination.vue'

const query = reactive({ pageNo: 1, pageSize: 10, type: '' })
const total = ref(0)
const list = ref([])
const showForm = ref(false)

const form = reactive({
  name: '',
  type: 1,
  category: '',
  difficulty: 1,
  analysis: '',
  answer: ''
})

const typeLabel = (t) => ({ 1: '单选', 2: '多选', 3: '判断' }[t] || t)
const diffLabel = (d) => ({ 1: '简单', 2: '中等', 3: '困难' }[d] || d)

const load = async () => {
  const data = await queryQuestionPage({
    pageNo: query.pageNo,
    pageSize: query.pageSize,
    type: query.type || undefined
  })
  total.value = data.total
  list.value = data.list
}

const onPageChange = ({ pageNo, pageSize }) => {
  query.pageNo = pageNo
  query.pageSize = pageSize
  load()
}

// 新建题目
const onSubmit = async () => {
  if (!form.name) {
    alert('请填写题干')
    return
  }
  try {
    await addQuestion({
      name: form.name,
      type: Number(form.type),
      category: form.category,
      difficulty: Number(form.difficulty),
      analysis: form.analysis,
      answer: form.answer
    })
    alert('创建成功')
    Object.assign(form, { name: '', category: '', analysis: '', answer: '' })
    showForm.value = false
    query.pageNo = 1
    load()
  } catch (e) {
    alert(e.message)
  }
}

// 删除题目
const onDelete = async (question) => {
  if (!confirm(`确定删除题目「${question.name}」？`)) return
  try {
    await deleteQuestion(question.id)
    load()
  } catch (e) {
    alert(e.message)
  }
}

// 绑定业务（课程/考试 id + 分值）
const onBind = async (question) => {
  const bizId = prompt('输入业务 id（课程/考试 id）')
  if (!bizId) return
  const score = prompt('输入分值（默认 0）', '0') || '0'
  try {
    await bindQuestionBiz(question.id, Number(bizId), Number(score))
    alert('绑定成功')
  } catch (e) {
    alert(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <div class="filters">
        <select v-model="query.type" @change="onPageChange({ pageNo: 1, pageSize: query.pageSize })">
          <option value="">全部题型</option>
          <option value="1">单选</option>
          <option value="2">多选</option>
          <option value="3">判断</option>
        </select>
      </div>
      <button v-btn-fx class="btn btn-primary" @click="showForm = !showForm">
        {{ showForm ? '收起' : '新建题目' }}
      </button>
    </div>

    <div v-if="showForm" class="panel">
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
          <input v-model="form.category" placeholder="如 microservice" />
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
      <button v-btn-fx class="btn btn-primary" @click="onSubmit">创建题目</button>
    </div>

    <div class="panel">
      <table class="q-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>题干</th>
            <th>题型</th>
            <th>分类</th>
            <th>难度</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in list" :key="q.id">
            <td>{{ q.id }}</td>
            <td class="q-name">{{ q.name }}</td>
            <td>{{ typeLabel(q.type) }}</td>
            <td>{{ q.category || '-' }}</td>
            <td>{{ diffLabel(q.difficulty) }}</td>
            <td>{{ q.status === 1 ? '启用' : '停用' }}</td>
            <td class="ops">
              <button v-btn-fx class="btn" @click="onBind(q)">绑定业务</button>
              <button v-btn-fx class="btn btn-danger" @click="onDelete(q)">删除</button>
            </td>
          </tr>
          <tr v-if="list.length === 0">
            <td colspan="7" class="empty-tip">暂无题目</td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="total" :page-no="query.pageNo" :page-size="query.pageSize" @page-change="onPageChange" />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-item {
  flex: 1;
}

.q-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.q-table th,
.q-table td {
  border-bottom: 1px solid #f0f0f0;
  padding: 10px 8px;
  text-align: left;
}

.q-table th {
  color: #666;
  background: #fafafa;
}

.q-name {
  max-width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ops {
  display: flex;
  gap: 6px;
}
</style>
