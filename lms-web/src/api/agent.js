import request from './request'
import { getToken } from '../utils/auth'

// ---------- 个人 Agent 聊天（0.2 前端 AI 入口） ----------

// 多轮对话：{ sessionId?, text, agentType: student|teacher }
export const agentChat = (data) => request.post('/agent/chat', data)

// POST SSE：EventSource 不支持 POST/Authorization，因此使用 fetch + ReadableStream 解析事件。
export const streamAgentChat = async (data, onEvent, signal) => {
  const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')
  const response = await fetch(`${base}/agent/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {})
    },
    body: JSON.stringify(data),
    signal
  })
  if (!response.ok || !response.body) {
    let message = `AI 请求失败（${response.status}）`
    try {
      const body = await response.json()
      message = body?.msg || message
    } catch (_) { /* 非 JSON 错误页 */ }
    throw new Error(message)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const consume = (block) => {
    let event = 'message'
    const dataLines = []
    block.split(/\r?\n/).forEach((line) => {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    })
    if (!dataLines.length) return
    const raw = dataLines.join('\n')
    try { onEvent(event, JSON.parse(raw)) } catch (_) { onEvent(event, raw) }
  }

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    blocks.forEach(consume)
    if (done) break
  }
  if (buffer.trim()) consume(buffer)
}

// 建会话
export const createAgentSession = () => request.post('/agent/sessions')

// 我的会话列表
export const myAgentSessions = () => request.get('/agent/sessions/mine')

export const getAgentTaskTree = (sessionId) => request.get('/agent/tasks/tree', { params: { sessionId } })

export const getHitlDetail = (requestId) => request.get(`/agent/harness/hitl/${requestId}`)

export const decideHitl = (requestId, decision) =>
  request.post(`/agent/harness/hitl/${requestId}/${decision}`)

// 课程知识库 RAG 问答（携带 courseId）
export const ragChat = (data) => request.post('/ai/chat/rag', data)
