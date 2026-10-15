<script setup lang="ts">
import { useUiStore, type Toast } from '@/stores/ui'

const ui = useUiStore()

function runAction(toast: Toast): void {
  toast.action?.run()
  ui.dismissToast(toast.id)
}
</script>

<template>
  <div class="toasts" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="toast in ui.toasts" :key="toast.id" class="toast" :class="`toast--${toast.kind}`">
        <span class="toast__message">{{ toast.message }}</span>
        <button v-if="toast.action" class="btn btn--sm" type="button" @click="runAction(toast)">
          {{ toast.action.label }}
        </button>
        <button
          class="btn btn--ghost btn--sm"
          type="button"
          aria-label="Закрыть уведомление"
          @click="ui.dismissToast(toast.id)"
        >
          ✕
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
