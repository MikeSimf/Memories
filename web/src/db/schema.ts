import { openDB, type DBSchema, type IDBPDatabase } from 'idb'

import type {
  ChangeOp,
  EntityLinkDto,
  HeroItemDto,
  MemberDto,
  MemberRole,
  ObjectClass,
  ObjectType,
  RetellingBriefDto,
  RetellingStatus,
  SyncConflictDto,
} from '@/api/types'

export const DB_NAME = 'dnd-memory'
/** Схема v2: четыре класса объектов вместо общего хранилища `entities` (`sync.md` §5). */
export const DB_VERSION = 4

/**
 * Имена хранилищ — по `sync.md` §5 («Локальное состояние клиента»).
 * `retelling_drafts` существует только на устройстве: черновики не покидают его.
 */
export const STORE = {
  objects: 'objects',
  retellings: 'retellings',
  retellingDrafts: 'retelling_drafts',
  pendingChanges: 'pending_changes',
  syncState: 'sync_state',
  members: 'members',
  campaign: 'campaign',
  meta: 'meta',
} as const

/** Хранилища прежней схемы — удаляются при обновлении до v2. */
const LEGACY_STORES = ['entities'] as const

export type StoreName = (typeof STORE)[keyof typeof STORE]

/**
 * Общие поля локальной записи объекта плюс служебные поля офлайна.
 * Поля своего класса добавляются в наследниках, различает их `objectType`.
 */
interface LocalObjectBase {
  id: string
  campaignId: string
  name: string
  description: string
  imageFileId: string | null
  /** Аватар: отдельная картинка, не из галереи. */
  avatarFileId: string | null
  links: EntityLinkDto[]
  createdBy: string
  createdByName: string
  updatedBy: string
  updatedByName: string
  revision: number
  createdAt: string
  updatedAt: string
  deleted: boolean
  /** 1 — есть неотправленная локальная правка (`sync.md` §5). */
  dirty: 0 | 1
  /** Расхождение с сервером после `REJECTED`. */
  conflict: SyncConflictDto | null
}

/** Герой: раса, класс, игрок и предметы в котомке. */
export interface LocalHero extends LocalObjectBase {
  objectType: 'HERO'
  race: string | null
  gender: string | null
  klass: string | null
  playerUserId: string | null
  playerName: string | null
  items: HeroItemDto[]
}

/** Персонаж (НПС): раса и краткое описание. */
export interface LocalCharacter extends LocalObjectBase {
  objectType: 'CHARACTER'
  race: string | null
  gender: string | null
  klass: string | null
  shortDescription: string | null
}

/** Локация: тип из справочника location_types. */
export interface LocalLocation extends LocalObjectBase {
  objectType: 'LOCATION'
  locationType: string | null
}

/** Предмет: владелец строкой и необязательная ссылка на героя или персонажа. */
export interface LocalItem extends LocalObjectBase {
  objectType: 'ITEM'
  itemType: string | null
  ownerName: string | null
  ownerHeroId: string | null
  ownerHeroName: string | null
  ownerCharacterId: string | null
  ownerCharacterName: string | null
}

export type LocalObject = LocalHero | LocalCharacter | LocalLocation | LocalItem

/** Локальная запись конкретного класса — по значению `ObjectClass`. */
export interface LocalObjectMap {
  HERO: LocalHero
  CHARACTER: LocalCharacter
  LOCATION: LocalLocation
  ITEM: LocalItem
}

export type LocalObjectOf<C extends ObjectClass> = LocalObjectMap[C]

/** Сужает запись до класса — удобно в интерфейсе. */
export function isLocalObjectOf<C extends ObjectClass>(
  object: LocalObject,
  objectClass: C,
): object is LocalObjectOf<C> {
  return object.objectType === objectClass
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
  createdByName: string
  updatedBy: string
  updatedByName: string
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

/** Очередь неотправленных правок (`sync.md` §5, `pending_changes`). */
export interface PendingChange {
  clientChangeId: string
  campaignId: string
  objectType: ObjectType
  objectId: string
  op: ChangeOp
  baseRevision: number | null
  /** Объект в форме DTO своего класса либо надгробие при `DELETE`. */
  payload: Record<string, unknown> | null
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
    objects: number
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
  ownerUserId: string | null
  myRole: MemberRole
  myPermissions: string[]
  memberCount: number
  /** Сумма объектов всех четырёх классов. */
  entityCount: number
  retellingCount: number
  latestRevision: number
  /** Краткие списки из `GET /api/campaigns/{id}` (`api.md` §4) — без лишних запросов. */
  members: MemberDto[]
  retellingBriefs: RetellingBriefDto[]
  createdAt: string | null
  updatedAt: string
  cachedAt: string
}

export interface MetaRecord {
  key: string
  value: string
}

export interface DndMemoryDB extends DBSchema {
  objects: {
    key: string
    value: LocalObject
    indexes: {
      'by-campaign': string
      'by-campaign-class': [string, ObjectClass]
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
      upgrade(db, oldVersion) {
        // Схема v1 хранила объекты в общем `entities`; в v2 у класса своя запись
        // и свой индекс `[campaignId, objectType]`.
        if (oldVersion < 2) {
          // `entities` есть только в схеме v1, поэтому имена хранилищ прежней версии
          // не входят в тип актуальной схемы и приводятся к нему явно.
          const names: readonly string[] = Array.from(db.objectStoreNames)
          for (const legacy of LEGACY_STORES) {
            if (names.includes(legacy)) db.deleteObjectStore(legacy as never)
          }
        }
        // Материалы на сайте работают только онлайн: локальное хранилище не нужно.
        // В v3 оно успело появиться, поэтому при обновлении удаляем его.
        if (oldVersion < 4 && Array.from(db.objectStoreNames).includes('materials' as never)) {
          db.deleteObjectStore('materials' as never)
        }
        if (!db.objectStoreNames.contains(STORE.objects)) {
          const objects = db.createObjectStore(STORE.objects, { keyPath: 'id' })
          objects.createIndex('by-campaign', 'campaignId')
          objects.createIndex('by-campaign-class', ['campaignId', 'objectType'])
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
    STORE.objects,
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
