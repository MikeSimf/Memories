import { openDB, type DBSchema, type IDBPDatabase } from 'idb'

import type {
  ChangeOp,
  EntityAttrs,
  EntityLinkDto,
  EntityType,
  MemberDto,
  MemberRole,
  ObjectType,
  RetellingStatus,
  SyncConflictDto,
} from '@/api/types'

export const DB_NAME = 'dnd-memory'
export const DB_VERSION = 1

/**
 * Имена хранилищ — по `sync.md` §4 («Локальное состояние клиента»).
 * `retelling_drafts` существует только на устройстве: черновики не покидают его.
 */
export const STORE = {
  entities: 'entities',
  retellings: 'retellings',
  retellingDrafts: 'retelling_drafts',
  pendingChanges: 'pending_changes',
  syncState: 'sync_state',
  members: 'members',
  campaign: 'campaign',
  meta: 'meta',
} as const

export type StoreName = (typeof STORE)[keyof typeof STORE]

/** Локальная запись объекта: полное состояние сущности + служебные поля офлайна. */
export interface LocalEntity {
  id: string
  campaignId: string
  clientId: string
  type: EntityType
  name: string
  summary: string
  description: string
  attrs: EntityAttrs
  tags: string[]
  imageFileId: string | null
  links: EntityLinkDto[]
  createdBy: string
  updatedBy: string
  revision: number
  createdAt: string
  updatedAt: string
  deleted: boolean
  /** 1 — есть неотправленная локальная правка (`sync.md` §4). */
  dirty: 0 | 1
  /** Расхождение с сервером после `REJECTED`. */
  conflict: SyncConflictDto | null
}

/** Локальная запись пересказа: только `PUBLISHED` (или отправляемый на сервер). */
export interface LocalRetelling {
  id: string
  campaignId: string
  clientId: string
  title: string
  body: string
  authorEntityId: string | null
  authorEntityName: string | null
  sessionDate: string | null
  status: RetellingStatus
  links: EntityLinkDto[]
  revision: number
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
  deleted: boolean
  dirty: 0 | 1
  conflict: SyncConflictDto | null
}

/** Черновик пересказа — живёт только в IndexedDB и никогда не уходит на сервер. */
export interface LocalRetellingDraft {
  id: string
  campaignId: string
  title: string
  body: string
  authorEntityId: string | null
  sessionDate: string | null
  createdAt: string
  updatedAt: string
  /** Всегда `DRAFT`; поле хранится, чтобы записи были однотипными в UI. */
  status: 'DRAFT'
}

/** Очередь неотправленных правок (`sync.md` §4, `pending_changes`). */
export interface PendingChange {
  clientChangeId: string
  campaignId: string
  objectType: ObjectType
  objectId: string
  op: ChangeOp
  baseRevision: number | null
  payload: Record<string, unknown>
  createdAt: string
  attempts: number
}

export interface SyncStateRecord {
  campaignId: string
  lastPulledRevision: number
  lastSyncAt: string | null
  lastError: string | null
  /** Заполняется после первичной загрузки snapshot. */
  snapshotLoaded: boolean
  counts: {
    entities: number
    retellings: number
    members: number
  } | null
}

export interface MemberRecord extends MemberDto {
  campaignId: string
  cachedAt: string
}

export interface CampaignRecord {
  id: string
  title: string
  description: string
  myRole: MemberRole
  myPermissions: string[]
  memberCount: number
  entityCount: number
  retellingCount: number
  latestRevision: number
  createdBy: string | null
  createdAt: string | null
  updatedAt: string
  cachedAt: string
}

export interface MetaRecord {
  key: string
  value: string
}

export interface DndMemoryDB extends DBSchema {
  entities: {
    key: string
    value: LocalEntity
    indexes: {
      'by-campaign': string
      'by-campaign-type': [string, EntityType]
    }
  }
  retellings: {
    key: string
    value: LocalRetelling
    indexes: {
      'by-campaign': string
      'by-campaign-status': [string, RetellingStatus]
    }
  }
  retelling_drafts: {
    key: string
    value: LocalRetellingDraft
    indexes: {
      'by-campaign': string
    }
  }
  pending_changes: {
    key: string
    value: PendingChange
    indexes: {
      'by-campaign': string
      'by-object': [string, string]
    }
  }
  sync_state: {
    key: string
    value: SyncStateRecord
  }
  members: {
    key: string
    value: MemberRecord
    indexes: {
      'by-campaign': string
    }
  }
  campaign: {
    key: string
    value: CampaignRecord
  }
  meta: {
    key: string
    value: MetaRecord
  }
}

let dbPromise: Promise<IDBPDatabase<DndMemoryDB>> | null = null

/** Открывает (и при необходимости создаёт) локальную базу. */
export function openDatabase(): Promise<IDBPDatabase<DndMemoryDB>> {
  if (!dbPromise) {
    dbPromise = openDB<DndMemoryDB>(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains(STORE.entities)) {
          const entities = db.createObjectStore(STORE.entities, { keyPath: 'id' })
          entities.createIndex('by-campaign', 'campaignId')
          entities.createIndex('by-campaign-type', ['campaignId', 'type'])
        }
        if (!db.objectStoreNames.contains(STORE.retellings)) {
          const retellings = db.createObjectStore(STORE.retellings, { keyPath: 'id' })
          retellings.createIndex('by-campaign', 'campaignId')
          retellings.createIndex('by-campaign-status', ['campaignId', 'status'])
        }
        if (!db.objectStoreNames.contains(STORE.retellingDrafts)) {
          const drafts = db.createObjectStore(STORE.retellingDrafts, { keyPath: 'id' })
          drafts.createIndex('by-campaign', 'campaignId')
        }
        if (!db.objectStoreNames.contains(STORE.pendingChanges)) {
          const pending = db.createObjectStore(STORE.pendingChanges, { keyPath: 'clientChangeId' })
          pending.createIndex('by-campaign', 'campaignId')
          pending.createIndex('by-object', ['campaignId', 'objectId'])
        }
        if (!db.objectStoreNames.contains(STORE.syncState)) {
          db.createObjectStore(STORE.syncState, { keyPath: 'campaignId' })
        }
        if (!db.objectStoreNames.contains(STORE.members)) {
          const members = db.createObjectStore(STORE.members, { keyPath: 'id' })
          members.createIndex('by-campaign', 'campaignId')
        }
        if (!db.objectStoreNames.contains(STORE.campaign)) {
          db.createObjectStore(STORE.campaign, { keyPath: 'id' })
        }
        if (!db.objectStoreNames.contains(STORE.meta)) {
          db.createObjectStore(STORE.meta, { keyPath: 'key' })
        }
      },
      blocked() {
        // Другая вкладка держит базу открытой со старой версией схемы.
        console.warn('[dnd-memory] обновление схемы IndexedDB заблокировано другой вкладкой')
      },
      terminated() {
        dbPromise = null
      },
    })
  }
  return dbPromise
}

/** Полная очистка локальной базы (выход из аккаунта, смена пользователя). */
export async function clearDatabase(): Promise<void> {
  const db = await openDatabase()
  const stores: StoreName[] = [
    STORE.entities,
    STORE.retellings,
    STORE.retellingDrafts,
    STORE.pendingChanges,
    STORE.syncState,
    STORE.members,
    STORE.campaign,
    STORE.meta,
  ]
  const tx = db.transaction(stores, 'readwrite')
  await Promise.all(stores.map((name) => tx.objectStore(name).clear()))
  await tx.done
}

/** Полное удаление базы — используется, если схема несовместима. */
export async function destroyDatabase(): Promise<void> {
  const db = await openDatabase()
  db.close()
  dbPromise = null
  const { deleteDB } = await import('idb')
  await deleteDB(DB_NAME)
}

/**
 * Сколько данных потеряется при очистке базы: черновики пересказов живут только
 * на устройстве, а очередь правок может ещё не уйти на сервер.
 */
export async function countLocalChanges(): Promise<{ drafts: number; pending: number }> {
  const db = await openDatabase()
  const [drafts, pending] = await Promise.all([
    db.count(STORE.retellingDrafts),
    db.count(STORE.pendingChanges),
  ])
  return { drafts, pending }
}

/** Служебные значения (последняя кампания, служебные флаги) в хранилище `meta`. */
export async function readMeta(key: string): Promise<string | null> {
  const db = await openDatabase()
  const record = await db.get(STORE.meta, key)
  return record?.value ?? null
}

export async function writeMeta(key: string, value: string): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.meta, { key, value })
}

/* ------------------------------------------------------------------ */
/* Шина изменений локальной базы                                       */
/* ------------------------------------------------------------------ */

type ChangeListener = (campaignId: string) => void

const listeners = new Set<ChangeListener>()

/**
 * Подписка на изменения локальной базы: синхронизация применяет пачку правок
 * и одним событием просит сторы перечитать данные.
 */
export function onDatabaseChanged(listener: ChangeListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function emitDatabaseChanged(campaignId: string): void {
  for (const listener of Array.from(listeners)) {
    try {
      listener(campaignId)
    } catch (error) {
      console.error('[dnd-memory] обработчик изменения базы упал', error)
    }
  }
}

export function nowIso(): string {
  return new Date().toISOString()
}

export type { ChangeOp, ObjectType }
