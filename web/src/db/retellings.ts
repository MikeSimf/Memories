import { openDatabase, nowIso, STORE, type LocalRetelling, type LocalRetellingDraft } from './schema'
import type { RetellingDto, SyncConflictDto } from '@/api/types'

export function retellingFromDto(
  dto: RetellingDto,
  extra: { dirty?: 0 | 1; conflict?: SyncConflictDto | null } = {},
): LocalRetelling {
  return {
    id: dto.id,
    campaignId: dto.campaignId,
    clientId: dto.clientId ?? dto.id,
    title: dto.title ?? '',
    body: dto.body ?? '',
    authorEntityId: dto.authorEntityId ?? null,
    authorEntityName: dto.authorEntityName ?? null,
    sessionDate: dto.sessionDate ?? null,
    status: dto.status ?? 'PUBLISHED',
    links: dto.links ?? [],
    revision: typeof dto.revision === 'number' ? dto.revision : 0,
    createdBy: dto.createdBy ?? '',
    createdByName: dto.createdByName ?? '',
    updatedBy: dto.updatedBy ?? '',
    updatedByName: dto.updatedByName ?? '',
    createdAt: dto.createdAt ?? nowIso(),
    updatedAt: dto.updatedAt ?? nowIso(),
    deleted: Boolean(dto.deleted),
    dirty: extra.dirty ?? 0,
    conflict: extra.conflict ?? null,
  }
}

export function retellingToPayload(retelling: LocalRetelling): Record<string, unknown> {
  return {
    id: retelling.id,
    campaignId: retelling.campaignId,
    clientId: retelling.clientId,
    title: retelling.title,
    body: retelling.body,
    authorEntityId: retelling.authorEntityId,
    sessionDate: retelling.sessionDate,
    // На сервер уходит только PUBLISHED: DRAFT не покидает устройство (`api.md` §7).
    status: 'PUBLISHED',
    createdAt: retelling.createdAt,
    updatedAt: retelling.updatedAt,
    deleted: retelling.deleted,
  }
}

/* ------------------------------- Публикации ------------------------------- */

export async function listRetellings(
  campaignId: string,
  options: { includeDeleted?: boolean } = {},
): Promise<LocalRetelling[]> {
  const db = await openDatabase()
  const items = await db.getAllFromIndex(STORE.retellings, 'by-campaign', campaignId)
  const filtered = options.includeDeleted ? items : items.filter((item) => !item.deleted)
  return filtered.sort((a, b) => {
    const dateA = a.sessionDate ?? a.updatedAt
    const dateB = b.sessionDate ?? b.updatedAt
    return dateB.localeCompare(dateA)
  })
}

export async function getRetelling(retellingId: string): Promise<LocalRetelling | null> {
  const db = await openDatabase()
  return (await db.get(STORE.retellings, retellingId)) ?? null
}

export async function putRetelling(retelling: LocalRetelling): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.retellings, retelling)
}

export async function putRetellings(retellings: LocalRetelling[]): Promise<void> {
  if (retellings.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.retellings, 'readwrite')
  await Promise.all(retellings.map((item) => tx.store.put(item)))
  await tx.done
}

export async function deleteRetellingRecord(retellingId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.retellings, retellingId)
}

export async function countRetellings(campaignId: string): Promise<number> {
  const db = await openDatabase()
  return db.countFromIndex(STORE.retellings, 'by-campaign', campaignId)
}

export async function pruneRetellings(campaignId: string, keepIds: Set<string>): Promise<number> {
  const local = await listRetellings(campaignId, { includeDeleted: true })
  const stale = local.filter((item) => !keepIds.has(item.id) && item.dirty === 0)
  if (stale.length === 0) return 0
  const db = await openDatabase()
  const tx = db.transaction(STORE.retellings, 'readwrite')
  await Promise.all(stale.map((item) => tx.store.delete(item.id)))
  await tx.done
  return stale.length
}

/* -------------------------------- Черновики ------------------------------- */

/**
 * Черновики пересказов: отдельное хранилище `retelling_drafts`, в синхронизацию
 * не попадают (`sync.md` §4) — «черновики не покидают устройство».
 */
export async function listDrafts(campaignId: string): Promise<LocalRetellingDraft[]> {
  const db = await openDatabase()
  const items = await db.getAllFromIndex(STORE.retellingDrafts, 'by-campaign', campaignId)
  return items.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
}

export async function getDraft(draftId: string): Promise<LocalRetellingDraft | null> {
  const db = await openDatabase()
  return (await db.get(STORE.retellingDrafts, draftId)) ?? null
}

export async function putDraft(draft: LocalRetellingDraft): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.retellingDrafts, draft)
}

export async function deleteDraft(draftId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.retellingDrafts, draftId)
}

export async function pruneDrafts(campaignId: string, keepIds: Set<string>): Promise<number> {
  const drafts = await listDrafts(campaignId)
  const stale = drafts.filter((draft) => !keepIds.has(draft.id))
  if (stale.length === 0) return 0
  const db = await openDatabase()
  const tx = db.transaction(STORE.retellingDrafts, 'readwrite')
  await Promise.all(stale.map((draft) => tx.store.delete(draft.id)))
  await tx.done
  return stale.length
}

/** Публикация черновика: черновик становится локальным `PUBLISHED` и уходит в очередь. */
export function draftToRetelling(
  draft: LocalRetellingDraft,
  extra: { createdBy: string; updatedBy: string; createdByName?: string; updatedByName?: string },
): LocalRetelling {
  const timestamp = nowIso()
  return {
    id: draft.id,
    campaignId: draft.campaignId,
    clientId: draft.id,
    title: draft.title,
    body: draft.body,
    authorEntityId: draft.authorEntityId,
    authorEntityName: null,
    sessionDate: draft.sessionDate,
    status: 'PUBLISHED',
    links: [],
    revision: 0,
    createdBy: extra.createdBy,
    createdByName: extra.createdByName ?? '',
    updatedBy: extra.updatedBy,
    updatedByName: extra.updatedByName ?? '',
    createdAt: draft.createdAt || timestamp,
    updatedAt: timestamp,
    deleted: false,
    dirty: 1,
    conflict: null,
  }
}
