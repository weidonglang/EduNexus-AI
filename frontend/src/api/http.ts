import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId?: string
  timestamp: string
}

export interface PageResponse<T> {
  records: T[]
  page: number
  size: number
  total: number
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 功能：请求发送前统一附加登录 token。
// 说明：业务页面不需要重复处理 Authorization，请求拦截器会从 auth store 中读取 accessToken，
// 后端 BearerTokenAuthenticationFilter 根据该 token 识别当前登录用户。
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

// 功能：统一处理后端响应和登录失效。
// 说明：后端返回 401/403 时清空本地会话并跳转登录页，避免用户继续停留在无权限页面。
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    if ((status === 401 || status === 403) && window.location.pathname !== '/login') {
      const auth = useAuthStore()
      auth.clearSession()
      window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
    }
    return Promise.reject(error)
  },
)
