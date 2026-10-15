<script setup lang="ts">
import BaseModal from './BaseModal.vue'

defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  busy?: boolean
}>()

const emit = defineEmits<{ (event: 'confirm'): void; (event: 'cancel'): void }>()
</script>

<template>
  <BaseModal :open="open" :title="title" @close="emit('cancel')">
    <p>{{ message }}</p>
    <template #actions>
      <button class="btn" type="button" :disabled="busy" @click="emit('cancel')">Отмена</button>
      <button class="btn btn--danger" type="button" :disabled="busy" @click="emit('confirm')">
        <span v-if="busy" class="spinner" aria-hidden="true" />
        {{ confirmLabel ?? 'Удалить' }}
      </button>
    </template>
  </BaseModal>
</template>
