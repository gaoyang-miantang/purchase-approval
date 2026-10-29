<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { session } from '../session'
import { notify } from '../toast'
import { categoryOptions, money, shortTime, statusOptions } from '../format'
import StatusTag from '../components/StatusTag.vue'
import RequestDrawer from '../components/RequestDrawer.vue'
import RequestForm from '../components/RequestForm.vue'

const router = useRouter()

const role = computed(() => session.user?.role || 'EMPLOYEE')
const isEmployee = computed(() => role.value === 'EMPLOYEE')
const canCreate = computed(() => role.value === 'EMPLOYEE')

const tabs = computed(() => {
  if (role.value === 'ADMIN') return [{ key: 'all', label: '全部申请' }]
  if (role.value === 'EMPLOYEE') {
    return [
      { key: 'mine', label: '我的申请' },
      { key: 'all', label: '全部申请' }
    ]
  }
  return [
    { key: 'todo', label: '待我审批' },
    { key: 'all', label: '全部申请' },
    { key: 'mine', label: '我的申请' }
  ]
})

const tab = ref(tabs.value[0].key)
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const busy = ref(false)
const stats = ref(null)
const demoDelay = ref(false)

const filters = reactive({ status: '', category: '', keyword: '' })

const detail = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)

const formVisible = ref(false)
const editing = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const tabCount = computed(() => ({
  todo: stats.value?.todoCount ?? 0,
  all: stats.value?.allCount ?? 0,
  mine: stats.value?.mineCount ?? 0
}))

async function loadStats() {
  try {
    stats.value = await api('/requests/stats')
  } catch (e) {
    // 统计失败不阻塞主流程，避免整页不可用
    console.warn('stats failed', e)
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await api('/requests', {
      params: {
        tab: tab.value,
        status: filters.status,
        category: filters.category,
        keyword: filters.keyword,
        page: page.value,
        size
      }
    })
    items.value = data.items
    total.value = data.total
  } catch (e) {
    notify(e.message, 'error', 4500)
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadList(), loadStats()])
}

function switchTab(key) {
  if (tab.value === key) return
  tab.value = key
  page.value = 1
  refreshAll()
}

function applyFilters() {
  page.value = 1
  loadList()
}

function resetFilters() {
  filters.status = ''
  filters.category = ''
  filters.keyword = ''
  page.value = 1
  loadList()
}

function goPage(next) {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  loadList()
}

async function openDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await api(`/requests/${id}`)
  } catch (e) {
    notify(e.message, 'error')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  editing.value = null
  formVisible.value = true
}

function openEdit() {
  if (!detail.value) return
  editing.value = { ...detail.value.request }
  formVisible.value = true
}

async function handleSave(payload) {
  busy.value = true
  try {
    if (editing.value) {
      await api(`/requests/${editing.value.id}`, { method: 'PUT', body: payload })
      notify('申请单已更新')
    } else {
      await api('/requests', { method: 'POST', body: payload })
      notify('申请单已保存为草稿')
    }
    formVisible.value = false
    editing.value = null
    await refreshAll()
  } catch (e) {
    notify(e.message, 'error', 4500)
  } finally {
    busy.value = false
  }
}

async function handleAction({ type, id, comment }) {
  busy.value = true
  try {
    const headers = demoDelay.value ? { 'X-Demo-Delay-Ms': '1500' } : {}
    if (type === 'approve') {
      await api(`/requests/${id}/approve`, { method: 'POST', body: { comment: comment || null }, headers })
      notify('审批通过')
    } else if (type === 'reject') {
      await api(`/requests/${id}/reject`, { method: 'POST', body: { reason: comment }, headers })
      notify('已驳回')
    } else if (type === 'submit') {
      await api(`/requests/${id}/submit`, { method: 'POST' })
      notify('已提交审批')
    } else if (type === 'cancel') {
      await api(`/requests/${id}/cancel`, { method: 'POST', body: { reason: comment || '申请人撤回' } })
      notify('申请单已作废')
    }
    await refreshAll()
    if (detailVisible.value) await openDetail(id)
  } catch (e) {
    notify(e.message, 'error', 5000)
    await refreshAll()
  } finally {
    busy.value = false
  }
}

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } catch {
    // 令牌可能已过期，忽略即可
  }
  session.logout()
  router.push('/login')
}

onMounted(refreshAll)
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="row gap-12">
          <div class="logo-mark">采</div>
          <div class="logo-text">
            <strong>采购审批中心</strong>
            <span>Procurement Approval</span>
          </div>
        </div>

        <div class="row gap-12">
          <span class="env-chip">演示环境</span>
          <div class="user-chip">
            <span class="avatar">{{ session.user?.displayName?.slice(0, 1) }}</span>
            <div class="user-meta">
              <strong>{{ session.user?.displayName }}</strong>
              <em>{{ session.user?.deptAndRole }}</em>
            </div>
          </div>
          <button class="btn btn-sm" type="button" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="page">
      <section class="stat-grid">
        <article class="stat-card">
          <span class="stat-label">待我处理</span>
          <strong class="stat-value">{{ stats?.pendingForMe ?? '—' }}</strong>
          <span class="stat-hint">需要我审批的单据</span>
        </article>
        <article class="stat-card">
          <span class="stat-label">我的进行中</span>
          <strong class="stat-value">{{ stats?.myOngoing ?? '—' }}</strong>
          <span class="stat-hint">我发起、尚未结束</span>
        </article>
        <article class="stat-card">
          <span class="stat-label">可见单据</span>
          <strong class="stat-value">{{ stats?.total ?? '—' }}</strong>
          <span class="stat-hint">按当前角色数据范围</span>
        </article>
        <article class="stat-card accent">
          <span class="stat-label">本月已通过金额</span>
          <strong class="stat-value">{{ stats ? money(stats.monthApprovedAmount) : '—' }}</strong>
          <span class="stat-hint">审批完成并计入预算</span>
        </article>
      </section>

      <section class="panel">
        <div class="panel-head">
          <nav class="tabs">
            <button
              v-for="item in tabs"
              :key="item.key"
              class="tab"
              :class="{ active: tab === item.key }"
              type="button"
              @click="switchTab(item.key)"
            >
              {{ item.label }}
              <span class="badge" :class="{ 'badge-active': tab === item.key }">{{ tabCount[item.key] }}</span>
            </button>
          </nav>

          <div class="row gap-8">
            <label class="demo-toggle" title="打开后审批接口会延迟 1.5 秒返回，用于演示两人同时审批">
              <input v-model="demoDelay" type="checkbox" />
              <span>并发演示</span>
            </label>
            <button v-if="canCreate" class="btn btn-primary" type="button" @click="openCreate">新建申请</button>
          </div>
        </div>

        <div class="toolbar">
          <select v-model="filters.status" class="select" style="width: 150px" @change="applyFilters">
            <option value="">全部状态</option>
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>

          <select v-model="filters.category" class="select" style="width: 140px" @change="applyFilters">
            <option value="">全部类别</option>
            <option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>

          <input
            v-model="filters.keyword"
            class="input"
            style="width: 240px"
            placeholder="搜索单号或标题"
            @keyup.enter="applyFilters"
          />

          <button class="btn" type="button" @click="applyFilters">查询</button>
          <button class="btn btn-ghost" type="button" @click="resetFilters">重置</button>
        </div>

        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th style="width: 150px">单号</th>
                <th>申请标题</th>
                <th style="width: 100px">类别</th>
                <th style="width: 120px">金额</th>
                <th style="width: 100px">部门</th>
                <th style="width: 90px">申请人</th>
                <th style="width: 120px">状态</th>
                <th style="width: 120px">更新时间</th>
              </tr>
            </thead>
            <tbody v-if="loading">
              <tr v-for="n in 5" :key="n">
                <td colspan="8"><div class="skeleton"></div></td>
              </tr>
            </tbody>
            <tbody v-else-if="items.length">
              <tr v-for="row in items" :key="row.id" @click="openDetail(row.id)">
                <td class="cell-code">{{ row.code }}</td>
                <td><div class="cell-title">{{ row.title }}</div></td>
                <td class="muted">{{ row.categoryLabel }}</td>
                <td class="cell-amount">{{ money(row.amount) }}</td>
                <td class="muted">{{ row.department }}</td>
                <td>{{ row.applicantName }}</td>
                <td><StatusTag :status="row.status" :label="row.statusLabel" /></td>
                <td class="muted small">{{ shortTime(row.updatedAt) }}</td>
              </tr>
            </tbody>
          </table>

          <div v-if="!loading && !items.length" class="empty">
            <p class="empty-title">暂无数据</p>
            <p v-if="tab === 'todo'">当前没有需要你处理的申请单</p>
            <p v-else>换个筛选条件试试</p>
          </div>
        </div>

        <footer class="pager">
          <span class="small muted">共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
          <div class="row gap-8">
            <button class="btn btn-sm" type="button" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
            <button class="btn btn-sm" type="button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
          </div>
        </footer>
      </section>

      <p class="foot-note">
        金额 ≥ 5000 元的申请在主管审批后自动进入财务复核；所有状态变更均写入审批流水，不可篡改。
      </p>
    </main>

    <RequestDrawer
      :visible="detailVisible"
      :detail="detail"
      :loading="detailLoading"
      :busy="busy"
      @close="detailVisible = false"
      @action="handleAction"
    />

    <RequestForm
      :visible="formVisible"
      :initial="editing"
      :busy="busy"
      @close="formVisible = false"
      @save="handleSave"
    />

    <button v-if="detailVisible && detail?.request?.canEdit" class="edit-fab" type="button" @click="openEdit">
      编辑草稿
    </button>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  background: var(--bg);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}

.topbar-inner {
  max-width: 1240px;
  margin: 0 auto;
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(140deg, #2563eb, #1e40af);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 6px 14px -8px rgba(37, 99, 235, 0.9);
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.logo-text strong {
  font-size: 14.5px;
  letter-spacing: 0.01em;
}

.logo-text span {
  font-size: 10.5px;
  color: var(--muted-light);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.env-chip {
  font-size: 11px;
  color: var(--warning);
  background: var(--warning-soft);
  border: 1px solid #f3dfc0;
  border-radius: 999px;
  padding: 2px 9px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--primary-dark);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-meta strong {
  font-size: 12.5px;
}

.user-meta em {
  font-style: normal;
  font-size: 11px;
  color: var(--muted);
}

.page {
  max-width: 1240px;
  margin: 0 auto;
  padding: 22px 24px 60px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  box-shadow: var(--shadow-sm);
}

.stat-card.accent {
  background: linear-gradient(150deg, #1e3a8a, #2563eb);
  border-color: #1d4ed8;
  color: #fff;
}

.stat-label {
  font-size: 12px;
  color: var(--muted);
}

.stat-card.accent .stat-label,
.stat-card.accent .stat-hint {
  color: rgba(255, 255, 255, 0.72);
}

.stat-value {
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  font-size: 11.5px;
  color: var(--muted-light);
}

.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}

.tabs {
  display: flex;
  gap: 4px;
  background: #f4f6fb;
  padding: 3px;
  border-radius: 9px;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: none;
  background: transparent;
  padding: 6px 12px;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab:hover {
  color: var(--text);
}

.tab.active {
  background: var(--surface);
  color: var(--primary-dark);
  box-shadow: var(--shadow-sm);
}

.demo-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
  background: #f8fafc;
  border: 1px dashed var(--border-strong);
  border-radius: 8px;
  padding: 5px 10px;
  cursor: pointer;
}

.demo-toggle input {
  accent-color: var(--primary);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  flex-wrap: wrap;
  border-bottom: 1px solid #f1f4f9;
}

.table-wrap {
  min-height: 260px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-top: 1px solid #f1f4f9;
  background: #fcfdff;
}

.foot-note {
  margin: 16px 2px 0;
  font-size: 12px;
  color: var(--muted-light);
}

.edit-fab {
  position: fixed;
  right: 588px;
  bottom: 28px;
  z-index: 80;
  border: 1px solid var(--border-strong);
  background: var(--surface);
  color: var(--text);
  border-radius: 999px;
  padding: 9px 16px;
  font-size: 13px;
  box-shadow: var(--shadow);
  cursor: pointer;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .edit-fab {
    right: 24px;
  }
}

@media (max-width: 720px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
  .user-meta {
    display: none;
  }
}
</style>
