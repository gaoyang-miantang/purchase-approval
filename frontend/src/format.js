const currency = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
})

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
})

const shortFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
})

export function money(value) {
  return currency.format(Number(value || 0))
}

export function datetime(value) {
  return value ? dateTimeFormatter.format(new Date(value)) : '—'
}

export function shortTime(value) {
  return value ? shortFormatter.format(new Date(value)) : '—'
}

export const categoryOptions = [
  { value: 'OFFICE', label: '办公用品' },
  { value: 'IT_ASSET', label: 'IT设备' },
  { value: 'TRAVEL', label: '差旅' },
  { value: 'SERVICE', label: '服务采购' }
]

export const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_MANAGER', label: '待主管审批' },
  { value: 'PENDING_FINANCE', label: '待财务复核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已作废' }
]
