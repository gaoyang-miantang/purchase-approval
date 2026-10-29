import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { Message } from '@arco-design/web-vue'
import router from '../router'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// 在 CloudStudio/Vite proxy 模式下使用相对路径，生产部署时可通过环境变量配置
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：自动附加 token
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (response) => {
    const { code, message } = response.data as ApiResponse
    if (code !== 0) {
      Message.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  (error: AxiosError) => {
    if (!error.response) {
      Message.error('网络异常，请检查连接')
      return Promise.reject(error)
    }

    const status = error.response.status

    if (status === 401) {
      Message.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    } else if (status === 403) {
      Message.error('无权限访问')
    } else if (status === 404) {
      Message.error('请求的资源不存在')
    } else if (status >= 500) {
      Message.error('服务器异常，请稍后重试')
    } else {
      const data = error.response.data as ApiResponse
      Message.error(data?.message || `请求失败 (${status})`)
    }

    return Promise.reject(error)
  },
)

export default request
