import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  isOnline,
  isSyncing,
  loadSnapshot,
  onSyncNotice,
  onSyncStateChanged,
  readSyncStatus,
  startAutoSync,
  stopAutoSync,
  syncManually,
  type SyncStatus,
} from '@/sync/engine'
import { useUiStore } from './ui'

/**
 * Индикатор и управление синхронизацией (`sync.md` §5): автосинхронизация при
 * старте, по событию `online` и по таймеру, плюс кнопка ручной синхронизации.
 */
export const useSyncStore = defineStore('sync', () => {
  const ui = useUiStore()

  const status = ref<SyncStatus | null>(null)
  const campaignId = ref<string | null>(null)
  const busy = ref(false)
  let initialized = false

  const pendingCount = computed(() => status.value?.pendingCount ?? 0)
  const conflictCount = computed(() => status.value?.conflictCount ?? 0)
  const lastSyncAt = computed(() => status.value?.lastSyncAt ?? null)
  const lastError = computed(() => status.value?.lastError ?? null)
  const online = computed(() => status.value?.online ?? isOnline())
  const syncing = computed(() => status.value?.syncing ?? false)
  const hasPending = computed(() => pendingCount.value > 0)

  const label = computed(() => {
    if (!online.value) return 'Офлайн'
    if (syncing.value) return 'Синхронизация…'
    if (conflictCount.value > 0) return `Расхождений: ${conflictCount.value}`
    if (pendingCount.value > 0) return `К отправке: ${pendingCount.value}`
    return 'Синхронизировано'
  })

  function initialize(): void {
    if (initialized) return
    initialized = true
    onSyncNotice((notice) => {
      ui.pushToast(notice.kind, notice.message)
    })
    onSyncStateChanged((changedId) => {
      if (changedId === campaignId.value) {
        void refresh()
      }
    })
  }

  async function refresh(): Promise<void> {
    if (!campaignId.value) {
      status.value = null
      return
    }
    status.value = await readSyncStatus(campaignId.value)
  }

  /** Открытие кампании: подписки, старт автосинхронизации, первый статус. */
  async function start(id: string): Promise<void> {
    initialize()
    campaignId.value = id
    startAutoSync(id)
    await refresh()
  }

  function stop(): void {
    stopAutoSync()
    campaignId.value = null
    status.value = null
  }

  async function syncNow(): Promise<void> {
    if (!campaignId.value) return
    busy.value = true
    try {
      await syncManually(campaignId.value)
    } finally {
      busy.value = false
      await refresh()
    }
  }

  /** Принудительная полная перезагрузка через snapshot (первый вход, resync). */
  async function reloadFull(): Promise<void> {
    if (!campaignId.value) return
    busy.value = true
    try {
      await loadSnapshot(campaignId.value)
      ui.pushToast('success', 'Данные кампании загружены заново')
    } catch (error) {
      ui.pushToast('error', 'Не удалось загрузить данные кампании')
      console.error('[dnd-memory] snapshot не удался', error)
    } finally {
      busy.value = false
      await refresh()
    }
  }

  function isSyncingNow(): boolean {
    return campaignId.value ? isSyncing(campaignId.value) : false
  }

  return {
    status,
    campaignId,
    busy,
    pendingCount,
    conflictCount,
    lastSyncAt,
    lastError,
    online,
    syncing,
    hasPending,
    label,
    initialize,
    refresh,
    start,
    stop,
    syncNow,
    reloadFull,
    isSyncingNow,
  }
})
