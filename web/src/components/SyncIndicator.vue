<script setup lang="ts">
import { computed } from 'vue'

import { useSyncStore } from '@/stores/sync'
import { formatDateTime } from '@/utils/datetime'

const props = defineProps<{ campaignId: string }>()
const sync = useSyncStore()

const stateClass = computed(() => {
  if (!sync.online) return 'sync-indicator--offline'
  if (sync.syncing || sync.busy) return 'sync-indicator--busy'
  if (sync.conflictCount > 0) return 'sync-indicator--conflict'
  if (sync.lastError) return 'sync-indicator--error'
  if (sync.pendingCount > 0) return 'sync-indicator--pending'
  return 'sync-indicator--ok'
})

const tooltip = computed(() => {
  const lines: string[] = [sync.label]
  if (sync.lastSyncAt) lines.push(`Последняя синхронизация: ${formatDateTime(sync.lastSyncAt)}`)
  if (sync.pendingCount > 0) lines.push(`Не отправлено правок: ${sync.pendingCount}`)
  if (sync.conflictCount > 0) lines.push(`Расхождений с сервером: ${sync.conflictCount}`)
  if (sync.lastError) lines.push(`Ошибка: ${sync.lastError}`)
  lines.push('Нажмите, чтобы синхронизировать')
  return lines.join('\n')
})

async function onClick(): Promise<void> {
  if (props.campaignId) await sync.syncNow()
}
</script>

<template>
  <button
    class="sync-indicator"
    :class="stateClass"
    type="button"
    :title="tooltip"
    :aria-label="`Синхронизация: ${sync.label}`"
    @click="onClick"
  >
    <span class="sync-indicator__dot" aria-hidden="true" />
    <span>{{ sync.label }}</span>
  </button>
</template>
