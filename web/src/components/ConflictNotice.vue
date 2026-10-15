<script setup lang="ts">
import { computed } from 'vue'

import type { SyncConflictDto } from '@/api/types'
import { formatDateTime } from '@/utils/datetime'

const props = defineProps<{ conflict: SyncConflictDto | null; saving?: boolean }>()
const emit = defineEmits<{ (event: 'keep-server'): void; (event: 'keep-local'): void }>()

/**
 * Тексты по `sync.md` §3.1 и §4: при `REJECTED` показываем, почему правка
 * не применена, и даём выбрать серверную версию или перезаписать своей.
 */
const reasonText = computed(() => {
  switch (props.conflict?.reason) {
    case 'stale_base':
      return 'Версия этого объекта на сервере новее — ваша правка не применена.'
    case 'forbidden':
      return 'Сервер отклонил правку: недостаточно прав.'
    case 'deleted':
      return 'Объект удалён на сервере.'
    default:
      return 'Правка не применена: есть расхождение с сервером.'
  }
})

const serverUpdatedAt = computed(() => {
  const server = props.conflict?.server
  if (!server || typeof server !== 'object') return null
  const updatedAt = (server as Record<string, unknown>).updatedAt
  return typeof updatedAt === 'string' ? formatDateTime(updatedAt) : null
})
</script>

<template>
  <div v-if="conflict" class="notice notice--danger stack stack--sm">
    <strong>Есть расхождение с сервером</strong>
    <span>{{ reasonText }}</span>
    <span v-if="serverUpdatedAt" class="faint">Серверная версия от {{ serverUpdatedAt }}</span>
    <div class="notice__actions">
      <button class="btn btn--sm" type="button" :disabled="saving" @click="emit('keep-server')">
        Оставить серверную версию
      </button>
      <button class="btn btn--sm btn--primary" type="button" :disabled="saving" @click="emit('keep-local')">
        Перезаписать своей
      </button>
    </div>
  </div>
</template>
