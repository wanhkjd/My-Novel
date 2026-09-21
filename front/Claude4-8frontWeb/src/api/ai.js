import request from '@/utils/request'
import { getReaderToken } from '@/utils/auth'

const CONV_KEY = 'ai_companion_conversation_id'

function genId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'conv-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}
export function getConversationId() {
  let id = localStorage.getItem(CONV_KEY)
  if (!id) { id = genId(); localStorage.setItem(CONV_KEY, id) }
  return id
}
export function newConversation() {
  const id = genId(); localStorage.setItem(CONV_KEY, id); return id
}

export const chat = (message, conversationId) =>
  request.get('/front/ai/companion/chat', { params: { message, conversationId } })
export const clearMemory = (conversationId) =>
  request.delete('/front/ai/companion/chat/memory', { params: { conversationId } })
export const getChatHistory = (conversationId) =>
  request.get('/front/ai/companion/chat/history', { params: { conversationId } })
export const recommendSimilar = (count = 4) =>
  request.get('/front/ai/companion/recommend', { params: { count } })

export async function streamChat(message, conversationId, { onChunk, onDone, onError } = {}) {
  const token = getReaderToken()
  const base = import.meta.env.VITE_BASE_API_URL || '/api'
  const url = `${base}/front/ai/companion/chat/stream`
    + `?message=${encodeURIComponent(message)}`
    + `&conversationId=${encodeURIComponent(conversationId || '')}`
  try {
    const resp = await fetch(url, {
      method: 'GET',
      headers: { Authorization: token || '', Accept: 'text/event-stream' }
    })
    if (!resp.ok) {
      if (resp.status === 401 || resp.status === 403) throw new Error('登录状态已失效，请重新登录')
      throw new Error(`请求失败 (${resp.status})`)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) { if (buffer.trim()) consume(buffer, onChunk); onDone && onDone(); break }
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const p of parts) consume(p, onChunk)
    }
  } catch (e) { onError && onError(e) }
}
function consume(eventStr, onChunk) {
  if (!eventStr) return
  const dataLines = []
  for (const line of eventStr.split('\n')) {
    const t = line.trim()
    if (t.startsWith('data:')) dataLines.push(t.slice(5).replace(/^\s/, ''))
  }
  if (dataLines.length && onChunk) onChunk(dataLines.join('\n'))
}
