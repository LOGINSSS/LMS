import request from './request'

// ---------- 个人 Agent 聊天（0.2 前端 AI 入口） ----------

// 多轮对话：{ sessionId?, text, agentType: student|teacher }
export const agentChat = (data) => request.post('/agent/chat', data)

// 建会话
export const createAgentSession = () => request.post('/agent/sessions')

// 我的会话列表
export const myAgentSessions = () => request.get('/agent/sessions/mine')

// 课程知识库 RAG 问答（携带 courseId）
export const ragChat = (data) => request.post('/ai/chat/rag', data)
