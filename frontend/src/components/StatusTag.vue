<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true },
  label: { type: String, default: '' }
})

const classMap = {
  DRAFT: 'tag-draft',
  PENDING_MANAGER: 'tag-pending',
  PENDING_FINANCE: 'tag-finance',
  APPROVED: 'tag-approved',
  REJECTED: 'tag-rejected',
  CANCELLED: 'tag-cancelled'
}

const tagClass = computed(() => classMap[props.status] || 'tag-draft')
</script>

<template>
  <span class="tag" :class="tagClass">{{ label }}</span>
</template>
