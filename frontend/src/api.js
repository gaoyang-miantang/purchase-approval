import { session } from './session'

const BASE = '/api'

/**
 * 统一请求封装：
 * 1. 自动带令牌
 * 2. 统一解析 { success, code, message, data } 信封
 * 3. 401 自动登出，409 交给调用方提示"请刷新"
 */
export async function api(path, options = {}) {
  const { method = 'GET', body, headers = {}, params } = options

  let url = BASE + path
  if (params) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.append(key, value)
      }
    })
    const query = search.toString()
    if (query) url += `?${query}`
  }

  const response = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(session.token ? { Authorization: `Bearer ${session.token}` } : {}),
      ...headers
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })

  const text = await response.text()
  let payload = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = { message: text }
    }
  }

  if (!response.ok || (payload && payload.success === false)) {
    if (response.status === 401) {
      session.logout()
    }
    const error = new Error(payload?.message || `请求失败（HTTP ${response.status}）`)
    error.status = response.status
    error.code = payload?.code
    throw error
  }

  return payload ? payload.data : null
}
