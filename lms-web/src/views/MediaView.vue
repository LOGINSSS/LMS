<script setup>
// 媒资管理页：文件/视频上传、我的媒资列表（分页）、删除
import { ref, reactive, onMounted, nextTick } from 'vue'
import { uploadMedia, queryMediaPage, deleteMedia } from '../api/media'
import { useEntrance } from '../composables/useEntrance'
import Pagination from '../components/Pagination.vue'

const query = reactive({ pageNo: 1, pageSize: 8 })
const total = ref(0)
const list = ref([])
const uploading = ref(false)

const gridEl = ref(null)
const { play: playEntrance } = useEntrance(gridEl)

const load = async () => {
  const data = await queryMediaPage({ pageNo: query.pageNo, pageSize: query.pageSize })
  total.value = data.total
  list.value = data.list
  await nextTick()
  playEntrance()
}

// 选择文件后立即上传
const onFileChange = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  uploading.value = true
  try {
    await uploadMedia(file)
    alert('上传成功')
    query.pageNo = 1
    load()
  } catch (err) {
    alert(err.message)
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

const onDelete = async (media) => {
  if (!confirm(`确定删除 ${media.name}？`)) return
  try {
    await deleteMedia(media.id)
    load()
  } catch (err) {
    alert(err.message)
  }
}

const onPageChange = ({ pageNo, pageSize }) => {
  query.pageNo = pageNo
  query.pageSize = pageSize
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel upload-box">
      <label class="upload-btn">
        <input type="file" :disabled="uploading" @change="onFileChange" />
        <span>{{ uploading ? '上传中...' : '选择文件上传（图片/视频）' }}</span>
      </label>
      <p class="tip">支持图片与视频，单个文件最大 100MB</p>
    </div>

    <div v-if="list.length === 0" class="empty-tip">暂无媒资，请上传</div>
    <div v-else ref="gridEl" class="media-grid">
      <div v-for="media in list" :key="media.id" class="media-item">
        <div class="preview">
          <img v-if="media.type === 1 && media.url" :src="media.url" alt="图片预览" />
          <video v-else-if="media.type === 2 && media.url" :src="media.url" controls />
          <span v-else class="file-icon">{{ media.name }}</span>
        </div>
        <p class="name">{{ media.name }}</p>
        <p class="meta">{{ media.type === 1 ? '图片' : media.type === 2 ? '视频' : '文件' }} · {{ media.size }} B</p>
        <div class="ops">
          <a v-if="media.url" class="btn" :href="media.url" target="_blank" rel="noopener">查看</a>
          <button v-btn-fx class="btn btn-danger" @click="onDelete(media)">删除</button>
        </div>
      </div>
    </div>

    <Pagination :total="total" :page-no="query.pageNo" :page-size="query.pageSize" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.upload-box {
  text-align: center;
  padding: 28px;
}

.upload-btn {
  display: inline-block;
  padding: 10px 24px;
  border: 1px dashed #409eff;
  border-radius: 6px;
  color: #409eff;
  cursor: pointer;
  font-size: 15px;
}

.upload-btn input {
  display: none;
}

.tip {
  color: #999;
  font-size: 13px;
  margin-top: 8px;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.media-item {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.preview {
  height: 130px;
  background: #f0f2f5;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.preview img,
.preview video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-icon {
  color: #999;
  font-size: 13px;
  padding: 8px;
  word-break: break-all;
  text-align: center;
}

.name {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  color: #999;
  font-size: 12px;
  margin: 4px 0 10px;
}

.ops {
  display: flex;
  gap: 8px;
}
</style>
