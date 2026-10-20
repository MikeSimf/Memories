import { openDatabase, nowIso, STORE, type LocalObject, type LocalObjectOf } from './schema'
import type {
  CampaignObjectDto,
  CharacterDto,
  HeroDto,
  HeroItemDto,
  ItemDto,
  ObjectClass,
  ObjectDtoOf,
  SyncConflictDto,
} from '@/api/types'

/** Общие поля DTO, которые есть у всех четырёх классов (`api.md` §6). */
function commonFromDto(dto: CampaignObjectDto): {
  id: string
  campaignId: string
  name: string
  description: string
  imageFileId: string | null
  avatarFileId: string | null
  links: CampaignObjectDto['links']
  createdBy: string
  createdByName: string
  updatedBy: string
  updatedByName: string
  revision: number
  createdAt: string
  updatedAt: string
  deleted: boolean
} {
  return {
    id: dto.id,
    campaignId: dto.campaignId,
    name: dto.name ?? '',
    description: dto.description ?? '',
    imageFileId: dto.imageFileId ?? null,
    avatarFileId: dto.avatarFileId ?? null,
    links: dto.links ?? [],
    createdBy: dto.createdBy ?? '',
    createdByName: dto.createdByName ?? '',
    updatedBy: dto.updatedBy ?? '',
    updatedByName: dto.updatedByName ?? '',
    revision: typeof dto.revision === 'number' ? dto.revision : 0,
    createdAt: dto.createdAt ?? nowIso(),
    updatedAt: dto.updatedAt ?? nowIso(),
    deleted: Boolean(dto.deleted),
  }
}

/** Предметы котомки: ответ сервера и локальная запись совпадают по форме. */
function itemsFromDto(items: HeroItemDto[] | null | undefined): HeroItemDto[] {
  if (!Array.isArray(items)) return []
  return items
    .filter((item): item is HeroItemDto => typeof item === 'object' && item !== null)
    .map((item, index) => ({
      id: typeof item.id === 'string' && item.id !== '' ? item.id : String(index),
      name: typeof item.name === 'string' ? item.name : '',
      itemId: typeof item.itemId === 'string' ? item.itemId : null,
      quantity: typeof item.quantity === 'number' ? item.quantity : null,
      notes: typeof item.notes === 'string' ? item.notes : null,
    }))
}

/**
 * Локальная запись из DTO сервера: класс известен вызывающему, потому что поля
 * у классов разные (`api.md` §6).
 */
export function objectFromDto<C extends ObjectClass>(
  objectClass: C,
  dto: ObjectDtoOf<C>,
  extra: { dirty?: 0 | 1; conflict?: SyncConflictDto | null } = {},
): LocalObjectOf<C> {
  const common = commonFromDto(dto)
  const local = { ...common, dirty: extra.dirty ?? 0, conflict: extra.conflict ?? null }
  switch (objectClass) {
    case 'HERO': {
      const hero = dto as HeroDto
      return {
        ...local,
        objectType: 'HERO',
        race: hero.race ?? null,
        gender: hero.gender ?? null,
        klass: hero.klass ?? null,
        playerUserId: hero.playerUserId ?? null,
        playerName: hero.playerName ?? null,
        items: itemsFromDto(hero.items),
      } as LocalObjectOf<C>
    }
    case 'CHARACTER': {
      const character = dto as CharacterDto
      return {
        ...local,
        objectType: 'CHARACTER',
        race: character.race ?? null,
        gender: character.gender ?? null,
        klass: character.klass ?? null,
        shortDescription: character.shortDescription ?? null,
      } as LocalObjectOf<C>
    }
    case 'ITEM': {
      const item = dto as ItemDto
      return {
        ...local,
        objectType: 'ITEM',
        itemType: item.itemType ?? null,
        ownerName: item.ownerName ?? null,
        ownerHeroId: item.ownerHeroId ?? null,
        ownerHeroName: item.ownerHeroName ?? null,
        ownerCharacterId: item.ownerCharacterId ?? null,
        ownerCharacterName: item.ownerCharacterName ?? null,
      } as LocalObjectOf<C>
    }
    default:
      return { ...local, objectType: 'LOCATION' } as LocalObjectOf<C>
  }
}

/**
 * Полное состояние объекта для `payload` в push (`sync.md` §1): payload — объект
 * в форме DTO своего класса. Поля офлайна (`dirty`, `conflict`) не отправляются.
 */
export function objectToPayload(object: LocalObject): Record<string, unknown> {
  const common: Record<string, unknown> = {
    id: object.id,
    campaignId: object.campaignId,
    name: object.name,
    description: object.description,
    imageFileId: object.imageFileId,
    avatarFileId: object.avatarFileId,
    createdAt: object.createdAt,
    updatedAt: object.updatedAt,
    deleted: object.deleted,
  }
  switch (object.objectType) {
    case 'HERO':
      return {
        ...common,
        race: object.race,
        gender: object.gender,
        klass: object.klass,
        playerUserId: object.playerUserId,
        items: object.items.map((item) => ({
          name: item.name,
          itemId: item.itemId,
          quantity: item.quantity,
          notes: item.notes,
        })),
      }
    case 'CHARACTER':
      return {
        ...common,
        race: object.race,
        gender: object.gender,
        shortDescription: object.shortDescription,
      }
    case 'ITEM':
      return {
        ...common,
        ownerName: object.ownerName,
        ownerHeroId: object.ownerHeroId,
        ownerCharacterId: object.ownerCharacterId,
      }
    default:
      return common
  }
}

/** Надгробие для `op: "DELETE"` (`sync.md` §1). */
export function objectTombstone(
  object: Pick<LocalObject, 'id' | 'updatedAt' | 'objectType'>,
): Record<string, unknown> {
  return {
    id: object.id,
    objectType: object.objectType,
    deleted: true,
    updatedAt: object.updatedAt,
  }
}

/**
 * Чтение списка объектов кампании: один класс — по индексу
 * `[campaignId, objectType]`, несколько (вкладка «Персонажи») — объединением.
 */
export async function listObjects(
  campaignId: string,
  options: { classes?: ObjectClass[]; includeDeleted?: boolean } = {},
): Promise<LocalObject[]> {
  const db = await openDatabase()
  const classes = options.classes && options.classes.length > 0 ? options.classes : null
  let items: LocalObject[]
  if (classes && classes.length === 1) {
    items = await db.getAllFromIndex(STORE.objects, 'by-campaign-class', [campaignId, classes[0]])
  } else if (classes) {
    const chunks = await Promise.all(
      classes.map((objectClass) =>
        db.getAllFromIndex(STORE.objects, 'by-campaign-class', [campaignId, objectClass]),
      ),
    )
    const merged = new Map<string, LocalObject>()
    for (const chunk of chunks) {
      for (const item of chunk) merged.set(item.id, item)
    }
    items = [...merged.values()]
  } else {
    items = await db.getAllFromIndex(STORE.objects, 'by-campaign', campaignId)
  }
  const filtered = options.includeDeleted ? items : items.filter((item) => !item.deleted)
  return filtered.sort((a, b) => a.name.localeCompare(b.name, 'ru'))
}

export async function getObject(objectId: string): Promise<LocalObject | null> {
  const db = await openDatabase()
  return (await db.get(STORE.objects, objectId)) ?? null
}

export async function putObject(object: LocalObject): Promise<void> {
  const db = await openDatabase()
  await db.put(STORE.objects, object)
}

export async function putObjects(objects: LocalObject[]): Promise<void> {
  if (objects.length === 0) return
  const db = await openDatabase()
  const tx = db.transaction(STORE.objects, 'readwrite')
  await Promise.all(objects.map((object) => tx.store.put(object)))
  await tx.done
}

export async function deleteObjectRecord(objectId: string): Promise<void> {
  const db = await openDatabase()
  await db.delete(STORE.objects, objectId)
}

export async function countObjects(campaignId: string, objectClass?: ObjectClass): Promise<number> {
  const db = await openDatabase()
  if (objectClass) {
    return db.countFromIndex(STORE.objects, 'by-campaign-class', [campaignId, objectClass])
  }
  return db.countFromIndex(STORE.objects, 'by-campaign', campaignId)
}

/** Убирает записи, которых нет в свежем snapshot (`sync.md` §3.3). */
export async function pruneObjects(campaignId: string, keepIds: Set<string>): Promise<number> {
  const local = await listObjects(campaignId, { includeDeleted: true })
  const stale = local.filter((item) => !keepIds.has(item.id) && item.dirty === 0)
  if (stale.length === 0) return 0
  const db = await openDatabase()
  const tx = db.transaction(STORE.objects, 'readwrite')
  await Promise.all(stale.map((item) => tx.store.delete(item.id)))
  await tx.done
  return stale.length
}
