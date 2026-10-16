import { openDatabase, nowIso, STORE, type CampaignRecord } from './schema'
import type { CampaignDto, CampaignListItem } from '@/api/types'

/**
 * Карточка кампании: из `GET /api/campaigns` (кратко) или
 * `GET /api/campaigns/{id}` (детали с `members` и `retellings`, `api.md` §4).
 */
export function campaignRecordFromDto(
  dto: CampaignDto | CampaignListItem,
  options: {
    permissions?: string[]
    counts?: Partial<
      Pick<CampaignRecord, 'memberCount' | 'entityCount' | 'retellingCount' | 'latestRevision'>
    >
  } = {},
): CampaignRecord {
  const listItem = dto as CampaignListItem
  const details = dto as CampaignDto
  return {
    id: dto.id,
    title: dto.title,
    description: dto.description ?? '',
    ownerUserId: details.ownerUserId ?? null,
    myRole: dto.myRole,
    myPermissions: options.permissions ?? details.myPermissions ?? [],
    memberCount: options.counts?.memberCount ?? listItem.memberCount ?? 0,
    entityCount: options.counts?.entityCount ?? listItem.entityCount ?? 0,
    retellingCount: options.counts?.retellingCount ?? listItem.retellingCount ?? 0,
    latestRevision: options.counts?.latestRevision ?? listItem.latestRevision ?? 0,
    // Краткие списки приходят только в деталях кампании — сохраняем их,
    // чтобы участники и пересказы открывались без лишних запросов.
    members: Array.isArray(details.members) ? details.members : [],
    retellingBriefs: Array.isArray(details.retellings) ? details.retellings : [],
    createdAt: details.createdAt ?? null,
    updatedAt: dto.updatedAt ?? nowIso(),
    cachedAt: nowIso(),
  }
}

export async function getCampaignRecord(campaignId: string): Promise<CampaignRecord | null> {
  const db = await openDatabase()
  return (await db.get(STORE.campaign, campaignId)) ?? null
}

export async function putCampaignRecord(record: CampaignRecord): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.campaign, record)
}

/** Список кампаний, известных устройству (для офлайн-режима). */
export async function listCampaignRecords(): Promise<CampaignRecord[]> {
  const db = await openDatabase()
  const items = await db.getAll(STORE.campaign)
  return items.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
}

export async function deleteCampaignRecord(campaignId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.campaign, campaignId)
}

/** Полная очистка данных одной кампании на устройстве. */
export async function clearCampaignData(campaignId: string): Promise<void> {
  const db = await openDatabase()
  const tx = db.transaction(
    [
      STORE.objects,
      STORE.retellings,
      STORE.retellingDrafts,
      STORE.pendingChanges,
      STORE.members,
      STORE.syncState,
    ],
    'readwrite',
  )

  const objectStore = tx.objectStore(STORE.objects)
  const objectKeys = await objectStore.index('by-campaign').getAllKeys(campaignId)
  await Promise.all(objectKeys.map((key) => objectStore.delete(key)))

  const retellingStore = tx.objectStore(STORE.retellings)
  const retellingKeys = await retellingStore.index('by-campaign').getAllKeys(campaignId)
  await Promise.all(retellingKeys.map((key) => retellingStore.delete(key)))

  const draftStore = tx.objectStore(STORE.retellingDrafts)
  const draftKeys = await draftStore.index('by-campaign').getAllKeys(campaignId)
  await Promise.all(draftKeys.map((key) => draftStore.delete(key)))

  const pendingStore = tx.objectStore(STORE.pendingChanges)
  const pendingKeys = await pendingStore.index('by-campaign').getAllKeys(campaignId)
  await Promise.all(pendingKeys.map((key) => pendingStore.delete(key)))

  const memberStore = tx.objectStore(STORE.members)
  const memberKeys = await memberStore.index('by-campaign').getAllKeys(campaignId)
  await Promise.all(memberKeys.map((key) => memberStore.delete(key)))

  await tx.objectStore(STORE.syncState).delete(campaignId)
  await tx.done
}
