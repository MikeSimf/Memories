import { openDatabase, STORE, type SyncStateRecord } from './schema'

function defaultState(campaignId: string): SyncStateRecord {
  return {
    campaignId,
    lastPulledRevision: 0,
    lastSyncAt: null,
    lastError: null,
    snapshotLoaded: false,
    counts: null,
  }
}

export async function getSyncState(campaignId: string): Promise<SyncStateRecord> {
  const db = await openDatabase()
  const record = await db.get(STORE.syncState, campaignId)
  return record ?? defaultState(campaignId)
}

export async function putSyncState(state: SyncStateRecord): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.syncState, state)
}

export async function patchSyncState(
  campaignId: string,
  patch: Partial<Omit<SyncStateRecord, 'campaignId'>>,
): Promise<SyncStateRecord> {
  const current = await getSyncState(campaignId)
  const next: SyncStateRecord = { ...current, ...patch, campaignId }
  await putSyncState(next)
  return next
}

/** Полная перезагрузка: `resync_required` или первый вход на устройстве. */
export async function resetSyncState(campaignId: string): Promise<SyncStateRecord> {
  const next = defaultState(campaignId)
  await putSyncState(next)
  return next
}
