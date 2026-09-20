import request from '@/utils/request'
import { getToken } from '@/utils/auth'

const CONV_ID_KEY = 'ai_companion_conversation_id'

function genConversationId() {
  if (typeof window !== 'undefined' && window.crypto && window.crypto.randomUUID) {
    return window.crypto.randomUUID()
  }
  return 'conv-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

/**
 * 获取当前会话ID，无则生成并持久化
 */
export function getConversationId() {
  let id = localStorage.getItem(CONV_ID_KEY)
  if (!id) {
    id = genConversationId()
    localStorage.setItem(CONV_ID_KEY, id)
  }
  return id
}

/**
 * 开启新会话：生成并持久化新的会话ID，返回之
 */
export function newConversation() {
  const id = genConversationId()
  localStorage.setItem(CONV_ID_KEY, id)
  return id
}

/**
 * 清空指定会话的服务端对话记忆
 */
export function clearMemory(conversationId) {
  return request.delete('/front/ai/companion/chat/memory', {
    params: { conversationId }
  })
}

/**
 * 查询指定会话的历史消息
 */
export function getChatHistory(conversationId) {
  return request.get('/front/ai/companion/chat/history', {
    params: { conversationId }
  })
}

/**
 * 智能书童常规对话接口
 */
export function chatWithCompanion(message, conversationId) {
  return request.get('/front/ai/companion/chat', {
    params: { message, conversationId }
  })
}

/**
 * 读者个性化阅读偏好相似推荐
 */
export function getSimilarRecommendations(count = 4) {
  return request.get('/front/ai/companion/recommend', {
    params: { count }
  })
}

/**
 * 加入用户书架
 */
export function addToBookshelf(bookId) {
  return request.post(`/front/user/bookshelf/${bookId}`)
}

/**
 * 流式对话 SSE 接收处理函数 (基于原生 fetch + ReadableStream，标准解析 SSE 协议)
 */
export async function streamChatWithCompanion(message, conversationId, onChunk, onDone, onError) {
  const token = getToken()
  const baseUrl = process.env.VUE_APP_BASE_API_URL || '/api'
  const url = `${baseUrl}/front/ai/companion/chat/stream`
    + `?message=${encodeURIComponent(message)}`
    + `&conversationId=${encodeURIComponent(conversationId || '')}`

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': token || '',
        'Accept': 'text/event-stream'
      }
    })

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        throw new Error('登录状态已失效，请重新登录')
      }
      throw new Error(`请求失败 (${response.status})`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) {
          consumeSseEvent(buffer, onChunk)
        }
        if (onDone) onDone()
        break
      }

      buffer += decoder.decode(value, { stream: true })
      // SSE 事件以双换行 \n\n 隔开
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''

      for (const part of parts) {
        consumeSseEvent(part, onChunk)
      }
    }
  } catch (error) {
    if (onError) {
      onError(error)
    }
  }
}

/**
 * 解析单个 SSE 事件块并提取 data 内容
 */
function consumeSseEvent(eventStr, onChunk) {
  if (!eventStr) return
  const lines = eventStr.split('\n')
  const dataLines = []
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('data:')) {
      dataLines.push(trimmed.substring(5).replace(/^\s/, ''))
    }
  }
  if (dataLines.length > 0 && onChunk) {
    onChunk(dataLines.join('\n'))
  }
}
