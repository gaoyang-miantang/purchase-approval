<script setup>
import { toasts } from '../toast'
</script>

<template>
  <div class="toast-stack">
    <div v-for="item in toasts" :key="item.id" class="toast" :class="`toast-${item.type}`">
      <span>{{ item.message }}</span>
    </div>
  </div>
</template>
