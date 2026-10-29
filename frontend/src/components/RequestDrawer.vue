<script setup>
import { computed, ref, watch } from 'vue'
import StatusTag from './StatusTag.vue'
import { datetime, money } from '../format'

const props = defineProps({
  visible: { type: Boolean, default: false },
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'action'])

const mode = ref(null)
const comment = ref('')
const error = ref('')

watch(
  () => props.visible,
  () => {
    mode.value = null
    comment.value = ''
    error.value = ''
  }
)

const request = computed(() => props.detail?.request || null)
const records = computed(() => props.detail?.records || [])

function choose(next) {
  mode.value = mode.value === next ? null : next
  comment.value = ''
  error.value = ''
}

function confirm() {
  if (!request.value) return
  if (mode.value === 'reject' && !comment.value.trim()) {
    error.value = '驳回必须填写原因'
    return
  }
  error.value = ''
  emit('action', {
    type: mode.value,
    id: request.value.id,
    comment: comment.value.trim()
  })
}

function submitDraft() {
  emit('action', { type: 'submit', id: request.value.id })
}
</script>

<template>
  <div v-if="visible" class="overlay" @click.self="$emit('close')">
    <aside class="drawer">
      <header class="drawer-head">
        <div>
          <p class="drawer-code mono">{{ request?.code || '加载中…' }}</p>
          <h3 class="drawer-title">{{ request?.title }}</h3>
          <div class="row gap-8" style="margin-top: 8px">
            <StatusTag v-if="request" :status="request.status" :label="request.statusLabel" />
            <span v-if="request?.waitingFor" class="small muted">当前节点：{{ request.waitingFor }}</span>
          </div>
        </div>
        <button class="icon-btn" type="button" aria-label="关闭" @click="$emit('close')">×</button>
      </header>

      <div class="drawer-body">
        <div v-if="loading" class="loading-block">
          <div class="skeleton" style="width: 100%"></div>
          <div class="skeleton" style="width: 80%"></div>
          <div class="skeleton" style="width: 64%"></div>
        </div>

        <template v-else-if="request">
          <div class="amount-hero">
            <span class="small muted">申请金额</span>
            <strong>{{ money(request.amount) }}</strong>
          </div>

          <dl class="info-grid">
            <div>
              <dt>申请人</dt>
              <dd>{{ request.applicantName }}</dd>
            </div>
            <div>
              <dt>所属部门</dt>
              <dd>{{ request.department }}</dd>
            </div>
            <div>
              <dt>采购类别</dt>
              <dd>{{ request.categoryLabel }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ datetime(request.createdAt) }}</dd>
            </div>
          </dl>

          <section class="block">
            <h4>申请事由</h4>
            <p class="reason">{{ request.reason }}</p>
          </section>

          <section class="block">
            <h4>审批记录</h4>
            <ol class="timeline">
              <li v-for="record in records" :key="record.id">
                <span class="dot" :class="record.action.toLowerCase()"></span>
                <div class="timeline-body">
                  <div class="row gap-8" style="justify-content: space-between">
                    <strong>
                      {{ record.operatorName }}
                      <em class="muted small">{{ record.operatorRoleLabel }}</em>
                    </strong>
                    <span class="small muted">{{ datetime(record.createdAt) }}</span>
                  </div>
                  <p class="timeline-text">
                    {{ record.actionLabel }}
                    <span v-if="record.fromStatusLabel && record.fromStatus !== record.toStatus" class="muted">
                      （{{ record.fromStatusLabel }} → {{ record.toStatusLabel }}）
                    </span>
                  </p>
                  <p v-if="record.comment" class="timeline-comment">{{ record.comment }}</p>
                </div>
              </li>
            </ol>
          </section>
        </template>
      </div>

      <footer v-if="request" class="drawer-foot">
        <div v-if="mode" class="decision-box">
          <label class="small muted">
            {{ mode === 'reject' ? '驳回原因（必填）' : '审批意见（选填）' }}
          </label>
          <textarea
            v-model="comment"
            class="textarea"
            rows="2"
            :placeholder="mode === 'reject' ? '例如：预算不足，下季度再提' : '例如：同意，按预算执行'"
          ></textarea>
          <p v-if="error" class="field-error">{{ error }}</p>
          <div class="row gap-8" style="justify-content: flex-end">
            <button class="btn btn-sm" type="button" @click="mode = null">取消</button>
            <button
              class="btn btn-sm"
              :class="mode === 'reject' ? 'btn-danger' : 'btn-primary'"
              type="button"
              :disabled="busy"
              @click="confirm"
            >
              {{ busy ? '处理中…' : mode === 'reject' ? '确认驳回' : '确认通过' }}
            </button>
          </div>
        </div>

        <div class="row gap-8" style="justify-content: flex-end; flex-wrap: wrap">
          <button v-if="request.canSubmit" class="btn btn-primary" type="button" :disabled="busy" @click="submitDraft">
            提交审批
          </button>
          <button v-if="request.canApprove" class="btn btn-primary" type="button" @click="choose('approve')">通过</button>
          <button v-if="request.canReject" class="btn btn-outline-danger" type="button" @click="choose('reject')">驳回</button>
          <button v-if="request.canCancel" class="btn" type="button" :disabled="busy" @click="$emit('action', { type: 'cancel', id: request.id })">
            作废
          </button>
          <button class="btn btn-ghost" type="button" @click="$emit('close')">关闭</button>
        </div>
      </footer>
    </aside>
  </div>
</template>

<style scoped>
.drawer-code {
  margin: 0 0 2px;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: var(--muted-light);
}

.drawer-title {
  margin: 0;
  font-size: 17px;
  line-height: 1.4;
  font-weight: 600;
}

.loading-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 0;
}

.amount-hero {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px 18px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f2f6ff, #f8fbff);
  border: 1px solid #e2ebfd;
  margin-bottom: 18px;
}

.amount-hero strong {
  font-size: 26px;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: #14306b;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
  margin: 0 0 22px;
}

.info-grid dt {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 2px;
}

.info-grid dd {
  margin: 0;
  font-size: 13.5px;
  font-weight: 500;
}

.block {
  margin-bottom: 22px;
}

.block h4 {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.reason {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: #334155;
  background: #fafbfe;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px 14px;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
}

.timeline li {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 18px 0;
}

.timeline li::before {
  content: "";
  position: absolute;
  left: 4px;
  top: 16px;
  bottom: -2px;
  width: 1px;
  background: var(--border);
}

.timeline li:last-child::before {
  display: none;
}

.timeline .dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-top: 6px;
  flex: none;
  background: var(--muted-light);
  box-shadow: 0 0 0 3px #f1f5f9;
}

.timeline .dot.approve {
  background: var(--success);
  box-shadow: 0 0 0 3px var(--success-soft);
}

.timeline .dot.reject {
  background: var(--danger);
  box-shadow: 0 0 0 3px var(--danger-soft);
}

.timeline .dot.submit {
  background: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-soft);
}

.timeline .dot.cancel {
  background: #64748b;
}

.timeline-body {
  flex: 1;
  min-width: 0;
}

.timeline-body strong {
  font-size: 13px;
  font-weight: 600;
}

.timeline-body em {
  font-style: normal;
  margin-left: 6px;
}

.timeline-text {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: #475569;
}

.timeline-comment {
  margin: 7px 0 0;
  font-size: 12.5px;
  color: #334155;
  background: #f8fafc;
  border-left: 2px solid var(--border-strong);
  border-radius: 0 6px 6px 0;
  padding: 7px 10px;
}

.decision-box {
  flex-basis: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed var(--border);
  margin-bottom: 10px;
}
</style>
