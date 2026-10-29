import { reactive } from 'vue'

export const toasts = reactive([])

let seed = 0

export function notify(message, type = 'success', duration = 3000) {
  const id = ++seed
  toasts.push({ id, message, type })
  setTimeout(() => {
    const index = toasts.findIndex((t) => t.id === id)
    if (index >= 0) toasts.splice(index, 1)
  }, duration)
}
