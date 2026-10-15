import {
  ApiError,
  entitiesApi,
  errorMessage,
  getDeviceId,
  retellingsApi,
  syncApi,
  type CampaignDto,
  type SyncApplyStatus,
  type SyncChangeLogEntry,
  type SyncPullResponse,
  type SyncPushResponse,
  type SyncPushResult,
} from '@/api'
import {
  bumpAttempts,
  campaignRecordFromDto,
  countEntities,
  countMembers,
  countRetellings,
  deleteEntityRecord,
  deletePendingChange,
  deleteRetellingRecord,
  emitDatabaseChanged,
  entityFromDto,
  entityToPayload,
  getCampaignRecord,
  getEntity,
  getRetelling,
  getSyncState,
  listEntities,
  listPendingChanges,
  listRetellings,
  memberFromDto,
  nowIso,
  patchSyncState,
  pruneEntities,
  pruneMembers,
  pruneRetellings,
  putCampaignRecord,
  putEntities,
  putEntity,
  putMembers,
  putRetelling,
  putRetellings,
  retellingFromDto,
  retellingToPayload,
  type LocalEntity,
  type LocalRetelling,
  type MemberRecord,
  type PendingChange,
  type SyncStateRecord,
} from '@/db'
import { debounce } from '@/utils/debounce'
import { entityFromPayload, memberFromPayload, retellingFromPayload } from './payload'
import { enqueueChange } from './queue'

/** Причина запуска синхронизации — события из `sync.md` §5. */
export type SyncReason = 'startup' | 'online' | 'local-change' | 'manual' | 'timer'

export interface SyncOutcome {
  campaignId: string
  reason: SyncReason
  /** Сколько правок принято сервером. */
  applied: number
  /** Правок, победивших в конфликте (`CONFLICT_RESOLVED`). */
  conflictResolved: number
  /** Отклонённых правок (`REJECTED`) — по ним нужно решение пользователя. */
  rejected: number
  /** Сколько записей журнала применено из pull. */
  pulled: number
  /** Выполнялась ли полная загрузка через snapshot. */
  snapshot: boolean
  error: string | null
}

export interface SyncNotice {
  campaignId: string
  kind: 'info' | 'success' | 'warning' | 'error'
  message: string
}

export interface SyncStatus {
  campaignId: string
  online: boolean
  syncing: boolean
  pendingCount: number
  conflictCount: number
  lastPulledRevision: number
  lastSyncAt: string | null
  lastError: string | null
}

const PAGE_LIMIT = 500
const PUSH_BATCH = 100
const MAX_PULL_ITERATIONS = 200
const MAX_SNAPSHOT_ITERATIONS = 200
const AUTO_SYNC_INTERVAL_MS = 5 * 60 * 1000
const LOCAL_CHANGE_DEBOUNCE_MS = 3 * 1000

/* ------------------------------------------------------------------ */
/* Подписки                                                            */
/* ------------------------------------------------------------------ */

const noticeListeners = new Set<(notice: SyncNotice) => void>()
const stateListeners = new Set<(campaignId: string) => void>()

export function onSyncNotice(listener: (notice: SyncNotice) => void): () => void {
  noticeListeners.add(listener)
  return () => noticeListeners.delete(listener)
}

export function onSyncStateChanged(listener: (campaignId: string) => void): () => void {
  stateListeners.add(listener)
  return () => stateListeners.delete(listener)
}

function emitNotice(notice: SyncNotice): void {
  for (const listener of Array.from(noticeListeners)) {
    try {
      listener(notice)
    } catch (error) {
      console.error('[dnd-memory] обработчик уведомлений синхронизации упал', error)
    }
  }
}

function emitStateChanged(campaignId: string): void {
  for (const listener of Array.from(stateListeners)) {
    try {
      listener(campaignId)
    } catch (error) {
      console.error('[dnd-memory] обработчик состояния синхронизации упал', error)
    }
  }
}

export function isOnline(): boolean {
  return typeof navigator === 'undefined' ? true : navigator.onLine
}

/* ------------------------------------------------------------------ */
/* Состояние                                                           */
/* ------------------------------------------------------------------ */

const running = new Map<string, Promise<SyncOutcome>>()

export function isSyncing(campaignId: string): boolean {
  return running.has(campaignId)
}

/** Объекты и пересказы, по которым сервер отклонил правку (`REJECTED`). */
export async function countConflicts(campaignId: string): Promise<number> {
  const [entities, retellings] = await Promise.all([
    listEntities(campaignId, { includeDeleted: true }),
    listRetellings(campaignId, { includeDeleted: true }),
  ])
  return (
    entities.filter((item) => item.conflict !== null).length +
    retellings.filter((item) => item.conflict !== null).length
  )
}

export async function listConflictIds(campaignId: string): Promise<string[]> {
  const [entities, retellings] = await Promise.all([
    listEntities(campaignId, { includeDeleted: true }),
    listRetellings(campaignId, { includeDeleted: true }),
  ])
  return [
    ...entities.filter((item) => item.conflict !== null).map((item) => item.id),
    ...retellings.filter((item) => item.conflict !== null).map((item) => item.id),
  ]
}

/** Текущее состояние синхронизации кампании — для индикатора в шапке. */
export async function readSyncStatus(campaignId: string): Promise<SyncStatus> {
  const [state, pending, conflictCount] = await Promise.all([
    getSyncState(campaignId),
    listPendingChanges(campaignId),
    countConflicts(campaignId),
  ])
  return {
    campaignId,
    online: isOnline(),
    syncing: isSyncing(campaignId),
    pendingCount: pending.length,
    conflictCount,
    lastPulledRevision: state.lastPulledRevision,
    lastSyncAt: state.lastSyncAt,
    lastError: state.lastError,
  }
}

/* ------------------------------------------------------------------ */
/* Push (`sync.md` §3.1)                                               */
/* ------------------------------------------------------------------ */

interface PushSummary {
  applied: number
  conflictResolved: number
  rejected: number
}

function toPushChange(change: PendingChange) {
  return {
    clientChangeId: change.clientChangeId,
    objectType: change.objectType,
    objectId: change.objectId,
    op: change.op,
    baseRevision: change.baseRevision,
    payload: change.payload,
  }
}

async function pushAll(campaignId: string): Promise<PushSummary> {
  const summary: PushSummary = { applied: 0, conflictResolved: 0, rejected: 0 }
  const changes = await listPendingChanges(campaignId)
  if (changes.length === 0) return summary

  for (let offset = 0; offset < changes.length; offset += PUSH_BATCH) {
    const batch = changes.slice(offset, offset + PUSH_BATCH)
    let response: SyncPushResponse
    try {
      response = await syncApi.push(campaignId, {
        clientId: getDeviceId(),
        changes: batch.map(toPushChange),
      })
    } catch (error) {
      // Правки не теряются: они остаются в очереди до следующего цикла.
      await bumpAttempts(
        batch.map((change) => change.clientChangeId),
        campaignId,
      )
      throw error
    }

    if (!Array.isArray(response.results)) {
      await bumpAttempts(
        batch.map((change) => change.clientChangeId),
        campaignId,
      )
      throw new ApiError({
        status: 0,
        code: 'internal_error',
        message: 'Сервер не вернул результат синхронизации',
      })
    }

    const handled = new Set<string>()
    for (const result of response.results) {
      const change = batch.find((item) => item.clientChangeId === result.clientChangeId)
      if (!change) continue
      handled.add(change.clientChangeId)
      await deletePendingChange(change.clientChangeId)
      await applyPushResult(campaignId, change, result)
      countStatus(summary, result.status)
    }

    // Правки, по которым сервер не ответил, остаются в очереди на следующий цикл.
    const unhandled = batch.filter((change) => !handled.has(change.clientChangeId))
    if (unhandled.length > 0) {
      await bumpAttempts(
        unhandled.map((change) => change.clientChangeId),
        campaignId,
      )
    }
  }

  return summary
}

function countStatus(summary: PushSummary, status: SyncApplyStatus): void {
  if (status === 'APPLIED') summary.applied += 1
  else if (status === 'CONFLICT_RESOLVED') summary.conflictResolved += 1
  else summary.rejected += 1
}

async function applyPushResult(
  campaignId: string,
  change: PendingChange,
  result: SyncPushResult,
): Promise<void> {
  if (change.objectType === 'ENTITY') {
    const entity = await getEntity(change.objectId)
    if (!entity) return
    if (result.status === 'REJECTED') {
      await putEntity({ ...entity, dirty: 0, conflict: result.conflict ?? { reason: 'stale_base' } })
      emitNotice({
        campaignId,
        kind: 'warning',
        message: `«${entity.name}»: версия на сервере новее — правка не применена`,
      })
      return
    }
    await putEntity({
      ...entity,
      revision: result.revision ?? entity.revision,
      dirty: 0,
      conflict: null,
    })
    if (result.status === 'CONFLICT_RESOLVED') {
      emitNotice({
        campaignId,
        kind: 'info',
        message: `«${entity.name}»: ваша версия новее, конфликт разрешён в вашу пользу`,
      })
    }
    return
  }

  if (change.objectType === 'RETELLING') {
    const retelling = await getRetelling(change.objectId)
    if (!retelling) return
    if (result.status === 'REJECTED') {
      await putRetelling({
        ...retelling,
        dirty: 0,
        conflict: result.conflict ?? { reason: 'stale_base' },
      })
      emitNotice({
        campaignId,
        kind: 'warning',
        message: `Пересказ «${retelling.title}»: версия на сервере новее — правка не применена`,
      })
      return
    }
    await putRetelling({
      ...retelling,
      revision: result.revision ?? retelling.revision,
      dirty: 0,
      conflict: null,
    })
    if (result.status === 'CONFLICT_RESOLVED') {
      emitNotice({
        campaignId,
        kind: 'info',
        message: `Пересказ «${retelling.title}»: ваша версия новее, конфликт разрешён в вашу пользу`,
      })
    }
  }
}

/* ------------------------------------------------------------------ */
/* Pull (`sync.md` §3.2)                                               */
/* ------------------------------------------------------------------ */

async function pendingObjectIds(campaignId: string): Promise<Set<string>> {
  const changes = await listPendingChanges(campaignId)
  return new Set(changes.map((change) => change.objectId))
}

interface PullSummary {
  pulled: number
  latestRevision: number
  resyncRequired: boolean
}

async function pullAll(campaignId: string, state: SyncStateRecord): Promise<PullSummary> {
  // Локальные неотправленные правки приоритетнее: объект пропускаем, его версия
  // уйдёт на сервер следующим push и вернётся уже с серверной ревизией.
  const skipIds = await pendingObjectIds(campaignId)
  let since = state.lastPulledRevision
  let pulled = 0

  for (let iteration = 0; iteration < MAX_PULL_ITERATIONS; iteration += 1) {
    let response: SyncPullResponse
    try {
      response = await syncApi.pull(campaignId, since, PAGE_LIMIT, getDeviceId())
    } catch (error) {
      if (error instanceof ApiError && (error.status === 410 || error.code === 'resync_required')) {
        return { pulled, latestRevision: since, resyncRequired: true }
      }
      throw error
    }

    const changes: SyncChangeLogEntry[] = response.changes ?? []
    if (changes.length > 0) {
      await applyChangeLog(campaignId, changes, skipIds)
      pulled += changes.length
    }
    const lastRevision = changes.reduce((max, entry) => Math.max(max, entry.revision ?? 0), since)
    since = Math.max(since, response.latestRevision ?? 0, lastRevision)
    if (!response.hasMore) break
  }

  return { pulled, latestRevision: since, resyncRequired: false }
}

async function dirtyEntityIds(campaignId: string): Promise<Set<string>> {
  const items = await listEntities(campaignId, { includeDeleted: true })
  return new Set(items.filter((item) => item.dirty === 1).map((item) => item.id))
}

async function dirtyRetellingIds(campaignId: string): Promise<Set<string>> {
  const items = await listRetellings(campaignId, { includeDeleted: true })
  return new Set(items.filter((item) => item.dirty === 1).map((item) => item.id))
}

async function applyChangeLog(
  campaignId: string,
  changes: SyncChangeLogEntry[],
  skipIds: Set<string>,
): Promise<void> {
  const entities: LocalEntity[] = []
  const retellings: LocalRetelling[] = []
  const members: MemberRecord[] = []
  let campaignPatch: CampaignDto | null = null

  for (const entry of changes) {
    if (skipIds.has(entry.objectId)) continue
    const payload = entry.payload ?? {}
    if (entry.objectType === 'ENTITY') {
      const entity = entityFromPayload(campaignId, payload, entry.op)
      if (entity) entities.push(entity)
      continue
    }
    if (entry.objectType === 'RETELLING') {
      const retelling = retellingFromPayload(campaignId, payload, entry.op)
      if (retelling) retellings.push(retelling)
      continue
    }
    if (entry.objectType === 'MEMBER') {
      const member = memberFromPayload(campaignId, payload, entry.op)
      if (member) members.push(member)
      continue
    }
    if (entry.objectType === 'CAMPAIGN') {
      campaignPatch = payload as unknown as CampaignDto
    }
  }

  // Записи с неотправленными правками не перетираем.
  const skipEntity = entities.length > 0 ? await dirtyEntityIds(campaignId) : new Set<string>()
  const skipRetelling =
    retellings.length > 0 ? await dirtyRetellingIds(campaignId) : new Set<string>()

  await putEntities(entities.filter((item) => !skipEntity.has(item.id)))
  await putRetellings(retellings.filter((item) => !skipRetelling.has(item.id)))
  if (members.length > 0) {
    await putMembers(members)
  }
  if (campaignPatch) {
    const previous = await getCampaignRecord(campaignId)
    await putCampaignRecord(
      campaignRecordFromDto(
        {
          ...campaignPatch,
          myRole: campaignPatch.myRole ?? previous?.myRole ?? 'VIEWER',
          myPermissions: campaignPatch.myPermissions ?? previous?.myPermissions ?? [],
        },
        {
          counts: {
            memberCount: previous?.memberCount ?? 0,
            entityCount: previous?.entityCount ?? 0,
            retellingCount: previous?.retellingCount ?? 0,
            latestRevision: campaignPatch.latestRevision ?? previous?.latestRevision ?? 0,
          },
        },
      ),
    )
  }
  emitDatabaseChanged(campaignId)
}

/* ------------------------------------------------------------------ */
/* Snapshot (`sync.md` §3.3)                                           */
/* ------------------------------------------------------------------ */

export interface SnapshotSummary {
  entities: number
  retellings: number
  members: number
  latestRevision: number
}

/**
 * Полная загрузка: первый вход на устройстве, смена устройства или `resync_required`
 * (журнал изменений скомпактирован — `sync.md` §6).
 */
export async function loadSnapshot(campaignId: string): Promise<SnapshotSummary> {
  const entityIds = new Set<string>()
  const retellingIds = new Set<string>()
  const memberIds = new Set<string>()
  const entities: LocalEntity[] = []
  const retellings: LocalRetelling[] = []
  const members: MemberRecord[] = []
  let campaign: CampaignDto | null = null
  let latestRevision = 0
  let cursor: string | null = null

  for (let iteration = 0; iteration < MAX_SNAPSHOT_ITERATIONS; iteration += 1) {
    const response = await syncApi.snapshot(campaignId, PAGE_LIMIT, cursor)
    latestRevision = Math.max(latestRevision, response.latestRevision ?? 0)
    if (response.campaign) campaign = response.campaign
    for (const dto of response.entities ?? []) {
      entityIds.add(dto.id)
      entities.push(entityFromDto(dto))
    }
    for (const dto of response.retellings ?? []) {
      retellingIds.add(dto.id)
      retellings.push(retellingFromDto(dto))
    }
    for (const dto of response.members ?? []) {
      memberIds.add(dto.id)
      members.push(memberFromDto(campaignId, dto))
    }
    cursor = response.nextCursor ?? null
    if (!cursor) break
  }

  const skipEntity = await dirtyEntityIds(campaignId)
  const skipRetelling = await dirtyRetellingIds(campaignId)

  await putEntities(entities.filter((item) => !skipEntity.has(item.id)))
  await putRetellings(retellings.filter((item) => !skipRetelling.has(item.id)))
  await putMembers(members)
  // Записи, которых нет на сервере, удаляем — snapshot отражает полное состояние.
  await pruneEntities(campaignId, entityIds)
  await pruneRetellings(campaignId, retellingIds)
  await pruneMembers(campaignId, memberIds)

  const previous = await getCampaignRecord(campaignId)
  if (campaign || previous) {
    const fallback: CampaignDto = {
      id: campaignId,
      title: previous?.title ?? '',
      description: previous?.description ?? '',
      myRole: previous?.myRole ?? 'VIEWER',
      myPermissions: (previous?.myPermissions ?? []) as CampaignDto['myPermissions'],
      updatedAt: previous?.updatedAt ?? nowIso(),
    }
    await putCampaignRecord(
      campaignRecordFromDto(campaign ?? fallback, {
        permissions: campaign?.myPermissions ?? previous?.myPermissions ?? [],
        counts: {
          entityCount: entityIds.size,
          retellingCount: retellingIds.size,
          memberCount: memberIds.size,
          latestRevision,
        },
      }),
    )
  }

  await patchSyncState(campaignId, {
    lastPulledRevision: latestRevision,
    lastSyncAt: nowIso(),
    lastError: null,
    snapshotLoaded: true,
    counts: {
      entities: entityIds.size,
      retellings: retellingIds.size,
      members: memberIds.size,
    },
  })
  emitDatabaseChanged(campaignId)

  return {
    entities: entities.length,
    retellings: retellings.length,
    members: members.length,
    latestRevision,
  }
}

/* ------------------------------------------------------------------ */
/* Основной цикл                                                       */
/* ------------------------------------------------------------------ */

/**
 * Один цикл синхронизации: push → pull (`sync.md` §3). Порядок специально такой,
 * чтобы локальные правки получили серверные ревизии и следующий pull их не вернул.
 */
export function syncCampaign(
  campaignId: string,
  reason: SyncReason = 'manual',
): Promise<SyncOutcome> {
  const current = running.get(campaignId)
  if (current) return current
  const task = runSync(campaignId, reason).finally(() => {
    running.delete(campaignId)
    emitStateChanged(campaignId)
  })
  running.set(campaignId, task)
  return task
}

async function runSync(campaignId: string, reason: SyncReason): Promise<SyncOutcome> {
  const outcome: SyncOutcome = {
    campaignId,
    reason,
    applied: 0,
    conflictResolved: 0,
    rejected: 0,
    pulled: 0,
    snapshot: false,
    error: null,
  }

  if (!isOnline()) {
    outcome.error = 'Нет сети — работаем с локальной базой'
    await patchSyncState(campaignId, { lastError: outcome.error })
    emitStateChanged(campaignId)
    return outcome
  }

  try {
    const summary = await pushAll(campaignId)
    outcome.applied = summary.applied
    outcome.conflictResolved = summary.conflictResolved
    outcome.rejected = summary.rejected

    const state = await getSyncState(campaignId)
    if (!state.snapshotLoaded) {
      const snapshot = await loadSnapshot(campaignId)
      outcome.snapshot = true
      outcome.pulled = snapshot.entities + snapshot.retellings + snapshot.members
    } else {
      const pull = await pullAll(campaignId, state)
      if (pull.resyncRequired) {
        await loadSnapshot(campaignId)
        outcome.snapshot = true
      } else {
        outcome.pulled = pull.pulled
        await patchSyncState(campaignId, {
          lastPulledRevision: pull.latestRevision,
          lastSyncAt: nowIso(),
          lastError: null,
        })
        await refreshCampaignCounts(campaignId, pull.latestRevision)
      }
    }

    emitDatabaseChanged(campaignId)
    emitStateChanged(campaignId)
    return outcome
  } catch (error) {
    const message = errorMessage(error)
    outcome.error = message
    await patchSyncState(campaignId, { lastError: message })
    if (reason === 'manual') {
      emitNotice({ campaignId, kind: 'error', message: `Синхронизация не удалась: ${message}` })
    }
    emitStateChanged(campaignId)
    return outcome
  }
}

async function refreshCampaignCounts(campaignId: string, latestRevision: number): Promise<void> {
  const record = await getCampaignRecord(campaignId)
  if (!record) return
  const [entityCount, retellingCount, memberCount] = await Promise.all([
    countEntities(campaignId),
    countRetellings(campaignId),
    countMembers(campaignId),
  ])
  await putCampaignRecord({
    ...record,
    entityCount,
    retellingCount,
    memberCount,
    latestRevision: Math.max(latestRevision, record.latestRevision),
  })
}

/* ------------------------------------------------------------------ */
/* Разрешение конфликтов (`sync.md` §4)                                */
/* ------------------------------------------------------------------ */

/** «Оставить серверную версию»: применяем состояние сервера и снимаем флаг. */
export async function resolveConflictUseServer(
  campaignId: string,
  objectId: string,
): Promise<void> {
  const entity = await getEntity(objectId)
  if (entity?.conflict) {
    const conflict = entity.conflict
    if (conflict.server) {
      const restored = entityFromPayload(
        campaignId,
        conflict.server,
        conflict.server.deleted === true ? 'DELETE' : 'UPSERT',
      )
      if (restored) {
        await putEntity({
          ...restored,
          revision: conflict.serverRevision ?? restored.revision,
          dirty: 0,
          conflict: null,
        })
        emitDatabaseChanged(campaignId)
        return
      }
    }
    // Тела серверной версии нет — перечитываем объект с сервера.
    try {
      const dto = await entitiesApi.get(campaignId, objectId)
      await putEntity(entityFromDto(dto))
    } catch (error) {
      if (error instanceof ApiError && error.isNotFound) {
        await deleteEntityRecord(objectId)
      } else {
        throw error
      }
    }
    emitDatabaseChanged(campaignId)
    return
  }

  const retelling = await getRetelling(objectId)
  if (retelling?.conflict) {
    const conflict = retelling.conflict
    if (conflict.server) {
      const restored = retellingFromPayload(
        campaignId,
        conflict.server,
        conflict.server.deleted === true ? 'DELETE' : 'UPSERT',
      )
      if (restored) {
        await putRetelling({
          ...restored,
          revision: conflict.serverRevision ?? restored.revision,
          dirty: 0,
          conflict: null,
        })
        emitDatabaseChanged(campaignId)
        return
      }
    }
    try {
      const dto = await retellingsApi.get(campaignId, objectId)
      await putRetelling(retellingFromDto(dto))
    } catch (error) {
      if (error instanceof ApiError && error.isNotFound) {
        await deleteRetellingRecord(objectId)
      } else {
        throw error
      }
    }
    emitDatabaseChanged(campaignId)
  }
}

/** «Перезаписать своей версией»: новая правка с текущим `updatedAt` (`sync.md` §4). */
export async function resolveConflictUseLocal(campaignId: string, objectId: string): Promise<void> {
  const entity = await getEntity(objectId)
  if (entity?.conflict) {
    const updated: LocalEntity = { ...entity, conflict: null, dirty: 1, updatedAt: nowIso() }
    await putEntity(updated)
    await enqueueChange({
      campaignId,
      objectType: 'ENTITY',
      objectId: entity.id,
      op: entity.deleted ? 'DELETE' : 'UPSERT',
      baseRevision: entity.conflict.serverRevision ?? entity.revision,
      payload: entityToPayload(updated),
    })
    emitDatabaseChanged(campaignId)
    notifyLocalChange(campaignId)
    return
  }

  const retelling = await getRetelling(objectId)
  if (retelling?.conflict) {
    const updated: LocalRetelling = {
      ...retelling,
      conflict: null,
      dirty: 1,
      updatedAt: nowIso(),
    }
    await putRetelling(updated)
    await enqueueChange({
      campaignId,
      objectType: 'RETELLING',
      objectId: retelling.id,
      op: retelling.deleted ? 'DELETE' : 'UPSERT',
      baseRevision: retelling.conflict.serverRevision ?? retelling.revision,
      payload: retellingToPayload(updated),
    })
    emitDatabaseChanged(campaignId)
    notifyLocalChange(campaignId)
  }
}

/* ------------------------------------------------------------------ */
/* Запуск синхронизации: события из `sync.md` §5                       */
/* ------------------------------------------------------------------ */

const scheduledSync = debounce((campaignId: string) => {
  void syncCampaign(campaignId, 'local-change')
}, LOCAL_CHANGE_DEBOUNCE_MS)

/** Локальная правка: отложенная синхронизация через 3 секунды. */
export function notifyLocalChange(campaignId: string): void {
  emitStateChanged(campaignId)
  scheduledSync(campaignId)
}

let activeCampaignId: string | null = null
let timerId: ReturnType<typeof setInterval> | null = null

function handleOnline(): void {
  if (activeCampaignId) {
    emitStateChanged(activeCampaignId)
    void syncCampaign(activeCampaignId, 'online')
  }
}

function handleOffline(): void {
  if (activeCampaignId) emitStateChanged(activeCampaignId)
}

/** Автосинхронизация открытой кампании: старт, `online` и таймер раз в 5 минут. */
export function startAutoSync(campaignId: string): void {
  if (activeCampaignId === campaignId && timerId !== null) return
  stopAutoSync()
  activeCampaignId = campaignId
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  timerId = setInterval(() => {
    void syncCampaign(campaignId, 'timer')
  }, AUTO_SYNC_INTERVAL_MS)
  void syncCampaign(campaignId, 'startup')
}

export function stopAutoSync(): void {
  if (timerId !== null) {
    clearInterval(timerId)
    timerId = null
  }
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  activeCampaignId = null
}

export function getActiveCampaignId(): string | null {
  return activeCampaignId
}

/** Ручной запуск из интерфейса — с уведомлением о результате. */
export async function syncManually(campaignId: string): Promise<SyncOutcome> {
  const outcome = await syncCampaign(campaignId, 'manual')
  if (!outcome.error) {
    emitNotice({
      campaignId,
      kind: 'success',
      message:
        outcome.pulled > 0 || outcome.applied > 0
          ? `Синхронизация завершена: получено ${outcome.pulled}, отправлено ${outcome.applied}`
          : 'Всё уже синхронизировано',
    })
  }
  return outcome
}
