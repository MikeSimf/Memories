import { openDatabase, nowIso, STORE, type LocalMaterial } from './schema'
import type { AttachmentDto, MaterialDto } from '@/api/types'

/** Материал из DTO сервера в локальную запись. */
export function materialFromDto(dto: MaterialDto): LocalMaterial {
  return {
    id: dto.id,
    campaignId: dto.campaignId,
    name: dto.name ?? '',
    description: dto.description ?? '',
    imageFileId: dto.imageFileId ?? null,
    files: (dto.files ?? []) as AttachmentDto[],
    revision: dto.revision ?? 0,
    createdBy: dto.createdBy ?? '',
    createdByName: dto.createdByName ?? '',
    updatedBy: dto.updatedBy ?? '',
    updatedByName: dto.updatedByName ?? '',
    createdAt: dto.createdAt ?? nowIso(),
    updatedAt: dto.updatedAt ?? nowIso(),
    deleted: dto.deleted ?? false,
    dirty: 0,
    conflict: null,
  }
}

/** Материал в тело правки: форма совпадает с DTO (`sync.md` §1). */
export function materialToPayload(material: LocalMaterial): Record<string, unknown> {
  return {
    id: material.id,
    campaignId: material.campaignId,
    name: material.name,
    description: material.description,
    imageFileId: material.imageFileId,
    files: material.files,
    createdAt: material.createdAt,
    updatedAt: material.updatedAt,
  }
}

export async function listMaterials(campaignId: string): Promise<LocalMaterial[]> {
  const db = await openDatabase()
  const all = await db.getAllFromIndex(STORE.materials, 'by-campaign', campaignId)
  return all
    .filter((item) => !item.deleted)
    .sort((a, b) => a.name.localeCompare(b.name, 'ru'))
}

export async function getMaterial(id: string): Promise<LocalMaterial | null> {
  const db = await openDatabase()
  return (await db.get(STORE.materials, id)) ?? null
}

export async function putMaterial(material: LocalMaterial): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.materials, material)
}

export async function putMaterials(materials: LocalMaterial[]): Promise<void> {
  if (materials.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.materials, 'readwrite')
  await Promise.all([...materials.map((item) => tx.store.put(item)), tx.done])
}

export async function deleteMaterialRecord(id: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.materials, id)
}

/** id материалов с неотправленными правками: их не перетираем из журнала. */
export async function dirtyMaterialIds(campaignId: string): Promise<Set<string>> {
  const all = await listMaterials(campaignId)
  return new Set(all.filter((item) => item.dirty === 1).map((item) => item.id))
}

/** Удаляет материалы, которых больше нет на сервере (после snapshot). */
export async function pruneMaterials(campaignId: string, keepIds: Set<string>): Promise<void> {
  const all = await listMaterials(campaignId)
  const db = await openDatabase()
  for (const item of all) {
    if (!keepIds.has(item.id) && item.dirty !== 1) {
      await db.delete(STORE.materials, item.id)
    }
  }
}
