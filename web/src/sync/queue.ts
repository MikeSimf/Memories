import type { ChangeOp, ObjectType } from '@/api/types'
import {
  deletePendingChange,
  deletePendingChanges,
  listPendingChanges,
  putPendingChange,
  type PendingChange,
} from '@/db'
import { nowIso } from '@/db/schema'
import { createChangeId } from '@/utils/uuid'

/**
 * Очередь локальных правок (`pending_changes`).
 *
 * Правки накапливаются и схлопываются по объекту: в журнал уходит полное
 * состояние объекта, поэтому несколько правок подряд достаточно отправить одной.
 * Идентификатор изменения — ключ идемпотентности (`sync.md` §2).
 */

export interface EnqueueInput {
  campaignId: string
  objectType: ObjectType
  objectId: string
  op: ChangeOp
  baseRevision: number | null
  payload: Record<string, unknown>
}

export interface EnqueueResult {
  /** Запись очереди или `null`, если правка схлопнулась (объект создан и удалён офлайн). */
  change: PendingChange | null
  /** `true`, если объекта на сервере никогда не было — локальную запись можно удалить. */
  dropped: boolean
}

async function pendingForObject(campaignId: string, objectId: string): Promise<PendingChange[]> {
  const changes = await listPendingChanges(campaignId)
  return changes.filter((change) => change.objectId === objectId)
}

export async function enqueueChange(input: EnqueueInput): Promise<EnqueueResult> {
  const existing = await pendingForObject(input.campaignId, input.objectId)
  const neverSent = existing.length > 0 && existing.every((change) => change.attempts === 0)
  // Базовая ревизия — та, от которой пошла цепочка локальных правок.
  const baseRevision =
    existing.length > 0 && existing[0].baseRevision !== null
      ? existing[0].baseRevision
      : input.baseRevision

  const createdOffline =
    existing.length > 0 &&
    existing.every((change) => change.op === 'UPSERT' && change.baseRevision === null)

  if (input.op === 'DELETE' && createdOffline) {
    // Объект появился только на этом устройстве и удалён до синхронизации:
    // серверу о нём сообщать нечего.
    await deletePendingChanges(existing.map((change) => change.clientChangeId))
    return { change: null, dropped: true }
  }

  if (neverSent) {
    const [first, ...rest] = existing
    await deletePendingChanges(rest.map((change) => change.clientChangeId))
    const change: PendingChange = {
      ...first,
      op: input.op,
      baseRevision,
      payload: input.payload,
      attempts: 0,
    }
    await putPendingChange(change)
    return { change, dropped: false }
  }

  if (existing.length > 0) {
    // Ранее отправленные записи больше не нужны: новая правка несёт полное состояние.
    await deletePendingChanges(existing.map((change) => change.clientChangeId))
  }

  const change: PendingChange = {
    clientChangeId: createChangeId(),
    campaignId: input.campaignId,
    objectType: input.objectType,
    objectId: input.objectId,
    op: input.op,
    baseRevision,
    payload: input.payload,
    createdAt: nowIso(),
    attempts: 0,
  }
  await putPendingChange(change)
  return { change, dropped: false }
}

export async function dropPendingForObject(campaignId: string, objectId: string): Promise<void> {
  const existing = await pendingForObject(campaignId, objectId)
  await deletePendingChanges(existing.map((change) => change.clientChangeId))
}

export async function removePendingChange(clientChangeId: string): Promise<void> {
  await deletePendingChange(clientChangeId)
}
