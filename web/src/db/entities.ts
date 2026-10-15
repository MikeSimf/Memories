import { openDatabase, nowIso, STORE, type LocalEntity } from './schema'
import type { EntityDto, EntityType, SyncConflictDto } from '@/api/types'

/** Локальная запись из DTO сервера. */
export function entityFromDto(
  dto: EntityDto,
  extra: { dirty?: 0 | 1; conflict?: SyncConflictDto | null } = {},
): LocalEntity {
  return {
    id: dto.id,
    campaignId: dto.campaignId,
    clientId: dto.clientId ?? dto.id,
    type: dto.type,
    name: dto.name ?? '',
    summary: dto.summary ?? '',
    description: dto.description ?? '',
    attrs: dto.attrs ?? {},
    tags: dto.tags ?? [],
    imageFileId: dto.imageFileId ?? null,
    links: dto.links ?? [],
    createdBy: dto.createdBy ?? '',
    updatedBy: dto.updatedBy ?? '',
    revision: typeof dto.revision === 'number' ? dto.revision : 0,
    createdAt: dto.createdAt ?? nowIso(),
    updatedAt: dto.updatedAt ?? nowIso(),
    deleted: Boolean(dto.deleted),
    dirty: extra.dirty ?? 0,
    conflict: extra.conflict ?? null,
  }
}

/**
 * Полное состояние объекта для `payload` в push (`sync.md` §1: payload — состояние
 * объекта после операции). Поля офлайн-режима (`dirty`, `conflict`) не отправляются.
 */
export function entityToPayload(entity: LocalEntity): Record<string, unknown> {
  return {
    id: entity.id,
    campaignId: entity.campaignId,
    clientId: entity.clientId,
    type: entity.type,
    name: entity.name,
    summary: entity.summary,
    description: entity.description,
    attrs: entity.attrs,
    tags: entity.tags,
    imageFileId: entity.imageFileId,
    createdAt: entity.createdAt,
    updatedAt: entity.updatedAt,
    deleted: entity.deleted,
  }
}

/** Надгробие для `op: "DELETE"` (`sync.md` §1). */
export function tombstone(entity: Pick<LocalEntity, 'id' | 'updatedAt'>): Record<string, unknown> {
  return { id: entity.id, deleted: true, updatedAt: entity.updatedAt }
}

export async function listEntities(
  campaignId: string,
  options: { types?: EntityType[]; includeDeleted?: boolean } = {},
): Promise<LocalEntity[]> {
  const db = await openDatabase()
  const types = options.types && options.types.length > 0 ? options.types : null
  let items: LocalEntity[]
  if (types && types.length === 1) {
    // Один вид — читаем по индексу `[campaignId, type]` без полного перебора.
    items = await db.getAllFromIndex(STORE.entities, 'by-campaign-type', [campaignId, types[0]])
  } else if (types) {
    // Несколько видов (вкладка «Персонажи»): читаем каждый индекс и объединяем по `id`.
    const chunks = await Promise.all(
      types.map((type) =>
        db.getAllFromIndex(STORE.entities, 'by-campaign-type', [campaignId, type]),
      ),
    )
    const merged = new Map<string, LocalEntity>()
    for (const chunk of chunks) {
      for (const item of chunk) merged.set(item.id, item)
    }
    items = [...merged.values()]
  } else {
    items = await db.getAllFromIndex(STORE.entities, 'by-campaign', campaignId)
  }
  const filtered = options.includeDeleted ? items : items.filter((item) => !item.deleted)
  return filtered.sort((a, b) => a.name.localeCompare(b.name, 'ru'))
}

export async function getEntity(entityId: string): Promise<LocalEntity | null> {
  const db = await openDatabase()
  return (await db.get(STORE.entities, entityId)) ?? null
}

export async function putEntity(entity: LocalEntity): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.entities, entity)
}

export async function putEntities(entities: LocalEntity[]): Promise<void> {
  if (entities.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.entities, 'readwrite')
  await Promise.all(entities.map((entity) => tx.store.put(entity)))
  await tx.done
}

export async function deleteEntityRecord(entityId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.entities, entityId)
}

export async function countEntities(campaignId: string, type?: EntityType): Promise<number> {
  const db = await openDatabase()
  if (type) {
    return db.countFromIndex(STORE.entities, 'by-campaign-type', [campaignId, type])
  }
  return db.countFromIndex(STORE.entities, 'by-campaign', campaignId)
}

/** Убирает записи, которых нет в свежем snapshot (`sync.md` §3.3). */
export async function pruneEntities(campaignId: string, keepIds: Set<string>): Promise<number> {
  const local = await listEntities(campaignId, { includeDeleted: true })
  const stale = local.filter((item) => !keepIds.has(item.id) && item.dirty === 0)
  if (stale.length === 0) return 0
  const db = await openDatabase()
  const tx = db.transaction(STORE.entities, 'readwrite')
  await Promise.all(stale.map((item) => tx.store.delete(item.id)))
  await tx.done
  return stale.length
}
