import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  tokenForUrl, removeReaderToken, removeAdminToken, removeNickName, removeUid
} from '@/utils/auth'

// 解耦 router：由 main.js 注册跳转处理器，避免 request↔router 循环依赖，也让纯函数可脱离 router 单测
let unauthorizedHandler = null
export function setUnauthorizedHandler(fn) { unauthorizedHandler = fn }
function redirectTo(path) {
  if (unauthorizedHandler) unauthorizedHandler(path)
}

export const SUCCESS_CODE = '00000'
export const TOKEN_EXPIRED_CODE = 'A0230'

export function buildApiError(payload, raw) {
  const err = new Error((payload && payload.message) || '请求失败')
  err.name = 'ApiError'
  err.isApiError = true
  err.code = payload && payload.code
  err.data = payload && payload.data
  err.raw = raw || payload
  return err
}

// 纯逻辑：成功返回 body，业务失败抛 ApiError（不含副作用，供单测）
export function handleSuccess(response) {
  const body = response.data
  if (!body || typeof body !== 'object') {
    throw buildApiError({ message: '服务端异常' }, response)
  }
  if (String(body.code) !== SUCCESS_CODE) {
    throw buildApiError(body)
  }
  return body
}

const request = axios.create({
  baseURL: import.meta.env.VITE_BASE_API_URL,
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = tokenForUrl(config.url || '')
  if (token) config.headers.Authorization = token
  return config
}, (e) => Promise.reject(e))

request.interceptors.response.use(
  (response) => {
    try {
      return handleSuccess(response)
    } catch (err) {
      ElMessage.error(err.message)
      if (err.code === TOKEN_EXPIRED_CODE) {
        const isAdmin = (response.config.url || '').includes('/admin')
        if (isAdmin) { removeAdminToken() } else { removeReaderToken() }
        removeNickName(); removeUid()
        const path = isAdmin ? '/admin/login' : '/login'
        redirectTo(path)
      }
      return Promise.reject(err)
    }
  },
  (error) => {
    const apiErr = buildApiError({ message: '网络异常，请稍后重试', code: 'NETWORK_ERROR' }, error)
    ElMessage.error(apiErr.message)
    return Promise.reject(apiErr)
  }
)

export function putForm(url, content) {
  return request.put(url, new URLSearchParams({ content }), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
}

export default request
