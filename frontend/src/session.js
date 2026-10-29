import { reactive } from 'vue'

const TOKEN_KEY = 'pa_token'
const USER_KEY = 'pa_user'

/**
 * 会话状态。用 reactive 代替 Pinia：这个规模的系统一个模块足够，
 * 少一个依赖就少一处构建风险。
 */
export const session = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser(),

  get isLoggedIn() {
    return Boolean(this.token)
  },

  setAuth(token, user) {
    this.token = token
    this.user = user
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },

  logout() {
    this.token = ''
    this.user = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }
})

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}
