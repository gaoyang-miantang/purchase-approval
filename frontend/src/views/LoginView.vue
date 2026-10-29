<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { session } from '../session'
import { notify } from '../toast'

const router = useRouter()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

const accounts = [
  { username: 'zhangwei', name: '张伟', role: '研发部 · 员工' },
  { username: 'lijing', name: '李静', role: '研发部 · 主管' },
  { username: 'wangfang', name: '王芳', role: '财务部 · 财务' },
  { username: 'chenming', name: '陈明', role: '综合管理部 · 管理员' },
  { username: 'liuyang', name: '刘洋', role: '市场部 · 员工' },
  { username: 'zhaolei', name: '赵磊', role: '市场部 · 主管' }
]

async function submit() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const data = await api('/auth/login', {
      method: 'POST',
      body: { username: form.username.trim(), password: form.password }
    })
    session.setAuth(data.token, data.user)
    notify(`欢迎回来，${data.user.displayName}`)
    router.push('/workbench')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function useAccount(account) {
  form.username = account.username
  form.password = '123456'
  submit()
}
</script>

<template>
  <div class="login-page">
    <aside class="brand-side">
      <div class="brand-top">
        <div class="brand-mark">采</div>
        <span class="brand-name">ProcureFlow</span>
      </div>

      <div class="brand-copy">
        <h1>企业采购申请<br />审批平台</h1>
        <p>从申请、分级审批到财务复核，一条链路跑完。<br />规则可配置，操作全留痕。</p>
      </div>

      <ul class="brand-points">
        <li>
          <span class="dot"></span>
          <div>
            <strong>金额分级审批</strong>
            <p>5000 元以下主管终审，5000 元及以上追加财务复核</p>
          </div>
        </li>
        <li>
          <span class="dot"></span>
          <div>
            <strong>角色 + 数据范围双控</strong>
            <p>员工看自己，主管看本部门，财务看全量非草稿</p>
          </div>
        </li>
        <li>
          <span class="dot"></span>
          <div>
            <strong>并发安全</strong>
            <p>两人同时审批，只有一人生效，另一人被明确拒绝</p>
          </div>
        </li>
      </ul>

      <p class="brand-foot">AI Coding 实战案例 · Spring Boot 3 + Vue 3</p>
    </aside>

    <section class="form-side">
      <div class="login-card">
        <header>
          <h2>登录</h2>
          <p class="muted small">使用下方任一演示账号可直接进入</p>
        </header>

        <form @submit.prevent="submit">
          <div class="field">
            <label>用户名</label>
            <input v-model="form.username" class="input" placeholder="如 zhangwei" autocomplete="username" />
          </div>
          <div class="field">
            <label>密码</label>
            <input
              v-model="form.password"
              class="input"
              type="password"
              placeholder="演示密码 123456"
              autocomplete="current-password"
            />
          </div>
          <p v-if="error" class="field-error">{{ error }}</p>
          <button class="btn btn-primary login-btn" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录' }}
          </button>
        </form>

        <div class="divider"><span>演示账号 · 一键切换</span></div>

        <div class="account-grid">
          <button
            v-for="account in accounts"
            :key="account.username"
            class="account"
            type="button"
            @click="useAccount(account)"
          >
            <span class="account-avatar">{{ account.name.slice(0, 1) }}</span>
            <span class="account-meta">
              <strong>{{ account.name }}</strong>
              <em>{{ account.role }}</em>
            </span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(360px, 46%) 1fr;
  min-height: 100vh;
}

.brand-side {
  position: relative;
  padding: 48px 56px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #e8efff;
  background: radial-gradient(1200px 600px at -10% -20%, #3b82f6 0%, transparent 55%),
    radial-gradient(900px 500px at 110% 120%, #1e40af 0%, transparent 60%), #0f172a;
  overflow: hidden;
}

.brand-side::after {
  content: "";
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #fff;
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 16px;
}

.brand-name {
  font-weight: 600;
  letter-spacing: 0.08em;
  font-size: 15px;
}

.brand-copy {
  position: relative;
  z-index: 1;
  margin: 24px 0;
}

.brand-copy h1 {
  font-size: 34px;
  line-height: 1.3;
  margin: 0 0 16px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.brand-copy p {
  margin: 0;
  color: #b9c8e6;
  font-size: 14px;
  line-height: 1.8;
}

.brand-points {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: relative;
  z-index: 1;
}

.brand-points li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.brand-points .dot {
  width: 7px;
  height: 7px;
  margin-top: 8px;
  border-radius: 50%;
  background: #93c5fd;
  box-shadow: 0 0 0 4px rgba(147, 197, 253, 0.15);
  flex: none;
}

.brand-points strong {
  font-size: 14px;
  font-weight: 600;
}

.brand-points p {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: #9fb0d0;
  line-height: 1.7;
}

.brand-foot {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: 12px;
  color: #7d8fb3;
}

.form-side {
  display: grid;
  place-items: center;
  padding: 40px 24px;
  background: var(--bg);
}

.login-card {
  width: min(430px, 100%);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow);
}

.login-card header h2 {
  margin: 0 0 4px;
  font-size: 22px;
  letter-spacing: -0.01em;
}

.login-card header p {
  margin: 0 0 22px;
}

form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.login-btn {
  height: 40px;
  margin-top: 4px;
  font-size: 14px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0 16px;
  color: var(--muted-light);
  font-size: 12px;
}

.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: var(--border);
}

.account-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.account {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 9px;
  border: 1px solid var(--border);
  background: #fbfcfe;
  cursor: pointer;
  text-align: left;
  transition: all 0.14s ease;
}

.account:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
  transform: translateY(-1px);
}

.account-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #e6edfd;
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  flex: none;
}

.account-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.account-meta strong {
  font-size: 12.5px;
  font-weight: 600;
}

.account-meta em {
  font-style: normal;
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .brand-side {
    display: none;
  }
}
</style>
