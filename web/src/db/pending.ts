import { openDatabase, STORE, type PendingChange } from './schema'

/** Очередь неотправленных правок в порядке возникновения (`sync.md` §3). */
export async function listPendingChanges(campaignId: string): Promise<PendingChange[]> {
  const db = await openDatabase()
  const items = await db.getAllFromIndex(STORE.pendingChanges, 'by-campaign', campaignId)
  return items.sort((a, b) => {
    if (a.createdAt === b.createdAt) return a.clientChangeId.localeCompare(b.clientChangeId)
    return a.createdAt.localeCompare(b.createdAt)
  })
}

export async function countPendingChanges(campaignId: string): Promise<number> {
  const db = await openDatabase()
  return db.countFromIndex(STORE.pendingChanges, 'by-campaign', campaignId)
}

export async function getPendingChange(
  clientChangeId: string,
): Promise<PendingChange | null> {
  const db = await openDatabase()
  return (await db.get(STORE.pendingChanges, clientChangeId)) ?? null
}

export async function putPendingChange(change: PendingChange): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.pendingChanges, change)
}

export async function deletePendingChange(clientChangeId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.pendingChanges, clientChangeId)
}

export async function deletePendingChanges(clientChangeIds: string[]): Promise<void> {
  if (clientChangeIds.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.pendingChanges, 'readwrite')
  await Promise.all(clientChangeIds.map((id) => tx.store.delete(id)))
  await tx.done
}

export async function clearPendingChanges(campaignId: string): Promise<void> {
  const db = await openDatabase()
  const keys = await db.getAllKeysFromIndex(STORE.pendingChanges, 'by-campaign', campaignId)
  if (keys.length === 0) return
  const tx = db.transaction(STORE.pendingChanges, 'readwrite')
  await Promise.all(keys.map((key) => tx.store.delete(key)))
  await tx.done
}

export async function bumpAttempts(clientChangeIds: string[], campaignId: string): Promise<void> {
  const changes = await listPendingChanges(campaignId)
  const targets = changes.filter((change) => clientChangeIds.includes(change.clientChangeId))
  if (targets.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.pendingChanges, 'readwrite')
  await Promise.all(
    targets.map((change) =>
      tx.store.put({ ...change, attempts: change.attempts + 1 } satisfies PendingChange),
    ),
  )
  await tx.done
}
