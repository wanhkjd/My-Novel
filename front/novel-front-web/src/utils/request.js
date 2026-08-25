import axios from 'axios'
import router from '@/router'
import { ElMessage } from 'element-plus'
import { getToken, removeToken, removeNickName } from '@/utils/auth'

const SUCCESS_CODE = '00000'
const TOKEN_EXPIRED_CODE = 'A0230'

function createApiError(payload, rawError) {
  const message = payload && payload.message ? payload.message : '请求失败'
  const error = new Error(message)
  error.name = 'ApiError'
  error.code = payload && payload.code
  error.data = payload && payload.data
  error.ok = payload && payload.ok
  error.raw = rawError || payload
  error.isApiError = true
  return error
}

if (typeof window !== 'undefined' && !window.__NOVEL_API_ERROR_HANDLER__) {
  window.__NOVEL_API_ERROR_HANDLER__ = true
  window.addEventListener('unhandledrejection', (event) => {
    if (event.reason && event.reason.isApiError) {
      event.preventDefault()
    }
  })
}

axios.defaults.baseURL = process.env.VUE_APP_BASE_API_URL
axios.defaults.timeout = 10000
axios.defaults.withCredentials = true
axios.defaults.headers['X-Requested-With'] = 'XMLHttpRequest'
axios.defaults.headers.post['Content-Type'] = 'application/json'

axios.interceptors.request.use(config => {
  config.headers['Authorization'] = getToken()
  return config
}, error => {
  return Promise.reject(error)
})

axios.interceptors.response.use(res => {
  if (typeof res.data !== 'object') {
    const error = createApiError({ message: '服务端异常' }, res)
    ElMessage.error(error.message)
    return Promise.reject(error)
  }

  if (String(res.data.code) !== SUCCESS_CODE) {
    const error = createApiError(res.data)
    ElMessage.error(error.message)
    if (res.data.code === TOKEN_EXPIRED_CODE) {
      removeToken()
      removeNickName()
      const loginPath = router.currentRoute.value.path.startsWith('/admin') ? '/admin/login' : '/login'
      router.push({ path: loginPath }).catch(() => {})
    }
    return Promise.reject(error)
  }

  return res.data
}, error => {
  const apiError = createApiError({ message: '网络异常，请稍后重试', code: 'NETWORK_ERROR' }, error)
  ElMessage.error(apiError.message)
  return Promise.reject(apiError)
})

export default axios
