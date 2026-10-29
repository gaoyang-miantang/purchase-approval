<script setup>
import { reactive, ref, watch } from 'vue'
import { categoryOptions } from '../format'

const props = defineProps({
  visible: { type: Boolean, default: false },
  initial: { type: Object, default: null },
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'save'])

const form = reactive({ title: '', reason: '', category: 'OFFICE', amount: '' })
const error = ref('')

watch(
  () => props.visible,
  (open) => {
    if (!open) return
    form.title = props.initial?.title || ''
    form.reason = props.initial?.reason || ''
    form.category = props.initial?.category || 'OFFICE'
    form.amount = props.initial?.amount != null ? String(props.initial.amount) : ''
    error.value = ''
  }
)

function submit() {
  if (!form.title.trim()) {
    error.value = '请填写申请标题'
    return
  }
  if (!form.reason.trim()) {
    error.value = '请填写申请事由'
    return
  }
  const amount = Number(form.amount)
  if (!Number.isFinite(amount) || amount <= 0) {
    error.value = '金额必须是大于 0 的数字'
    return
  }
  error.value = ''
  emit('save', {
    title: form.title.trim(),
    reason: form.reason.trim(),
    category: form.category,
    amount: Number(amount.toFixed(2))
  })
}
</script>

<template>
  <div v-if="visible" class="modal-mask" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal-head">{{ initial ? '编辑申请单' : '新建采购申请' }}</div>

      <div class="modal-body">
        <div class="field">
          <label>申请标题</label>
          <input v-model="form.title" class="input" maxlength="120" placeholder="例如：研发部 Q4 办公用品采购" />
        </div>

        <div class="grid-2">
          <div class="field">
            <label>采购类别</label>
            <select v-model="form.category" class="select">
              <option v-for="item in categoryOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>金额（元）</label>
            <input v-model="form.amount" class="input" type="number" min="0.01" step="0.01" placeholder="0.00" />
          </div>
        </div>

        <div class="field">
          <label>申请事由</label>
          <textarea v-model="form.reason" class="textarea" maxlength="500" rows="4" placeholder="说明采购原因、用途和数量"></textarea>
        </div>

        <p v-if="error" class="field-error">{{ error }}</p>
        <p class="hint">
          保存后为草稿状态，提交后进入审批流；金额 ≥ 5000 元需要财务复核。
        </p>
      </div>

      <div class="modal-foot">
        <button class="btn" type="button" @click="$emit('close')">取消</button>
        <button class="btn btn-primary" type="button" :disabled="busy" @click="submit">
          {{ busy ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted-light);
  background: #f8fafc;
  border: 1px dashed var(--border);
  border-radius: 8px;
  padding: 9px 12px;
}
</style>
