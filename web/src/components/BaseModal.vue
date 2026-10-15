<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    /** Закрывать по клику на подложку. */
    dismissible?: boolean
  }>(),
  { title: '', dismissible: true },
)

const emit = defineEmits<{ (event: 'close'): void }>()

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.dismissible) emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      window.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

function onBackdrop(): void {
  if (props.dismissible) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="onBackdrop">
      <div class="modal" role="dialog" aria-modal="true" :aria-label="title || 'Диалог'">
        <header v-if="title || $slots.header" class="row row--between">
          <h2 class="modal__title">{{ title }}</h2>
          <slot name="header" />
          <button
            v-if="dismissible"
            class="btn btn--ghost btn--sm"
            type="button"
            aria-label="Закрыть"
            @click="emit('close')"
          >
            ✕
          </button>
        </header>
        <slot />
        <div v-if="$slots.actions" class="modal__actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
