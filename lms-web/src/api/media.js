import request from './request'

// 媒资上传（multipart，参数名 file）
export const uploadMedia = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/medias/upload', form)
}

// 我的媒资分页
export const queryMediaPage = (params) => request.get('/medias/page', { params })

// 删除媒资（本人）
export const deleteMedia = (id) => request.delete(`/medias/${id}`)
