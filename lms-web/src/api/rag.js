import request from './request'

// ---------- 课程知识库（Python RAG，走网关 /rag/** → python） ----------

// 某课程的知识文件列表（含 chunk 统计）
export const listCourseKbFiles = (courseId) => request.get(`/rag/kb/courses/${courseId}/files`)

// 上传知识文档到某课程知识库（异步任务，返回 { task_id }）
export const uploadCourseKbFile = (courseId, file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/rag/kb/courses/${courseId}/files`, fd)
}

// 入库任务进度（轮询直到 done/failed）
export const getKbTask = (taskId) => request.get(`/rag/kb/tasks/${taskId}`)

// 删除某课程下的一份知识文档
export const deleteCourseKbFile = (courseId, docId) =>
  request.delete(`/rag/kb/courses/${courseId}/files/${docId}`)

// 清空某课程知识库（删除课程时由后端调用，前端备用）
export const clearCourseKb = (courseId) => request.delete(`/rag/kb/courses/${courseId}`)
