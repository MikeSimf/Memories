import type {
  ChangeOp,
  CharacterDto,
  EntityLinkDto,
  HeroDto,
  HeroItemDto,
  ItemDto,
  LocationDto,
  MemberDto,
  MemberRole,
  MemberStatus,
  ObjectClass,
  RetellingStatus,
} from '@/api/types'
import { MEMBER_ROLES, MEMBER_STATUSES, OBJECT_CLASSES } from '@/api/types'
import { memberFromDto, objectFromDto, retellingFromDto } from '@/db'
import type { LocalObject, LocalRetelling, MemberRecord } from '@/db/schema'
import { nowIso } from '@/db/schema'

/**
 * Разбор `payload` из журнала синхронизации (`sync.md` §1): сервер присылает
 * полное состояние объекта в форме DTO своего класса, для `DELETE` — надгробие
 * `{ id, objectType, deleted, updatedAt }`.
 */

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function asOptionalString(value: unknown): string | null {
  return typeof value === 'string' && value !== '' ? value : null
}

function asNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function asOptionalNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function asBoolean(value: unknown, fallback = false): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function asLinks(value: unknown): EntityLinkDto[] {
  if (!Array.isArray(value)) return []
  const links: EntityLinkDto[] = []
  for (const item of value) {
    if (typeof item !== 'object' || item === null) continue
    const record = item as Record<string, unknown>
    links.push({
      targetId: asOptionalString(record.targetId),
      label: asString(record.label),
      resolved: asBoolean(record.resolved),
    })
  }
  return links
}

function asHeroItems(value: unknown): HeroItemDto[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const record = (typeof item === 'object' && item !== null ? item : {}) as Record<string, unknown>
    return {
      id: asString(record.id, String(index)),
      name: asString(record.name),
      itemId: asOptionalString(record.itemId),
      quantity: asOptionalNumber(record.quantity),
      notes: asOptionalString(record.notes),
    }
  })
}

export function isObjectClass(value: unknown): value is ObjectClass {
  return typeof value === 'string' && (OBJECT_CLASSES as readonly string[]).includes(value)
}

/** Общие поля DTO из `payload`. */
function commonPayload(
  campaignId: string,
  payload: Record<string, unknown>,
  id: string,
  timestamp: string,
  deleted: boolean,
): {
  id: string
  campaignId: string
  name: string
  description: string
  imageFileId: string | null
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
} {
  return {
    id,
    campaignId,
    name: asString(payload.name),
    description: asString(payload.description),
    imageFileId: asOptionalString(payload.imageFileId),
    avatarFileId: asOptionalString(payload.avatarFileId),
    links: asLinks(payload.links),
    createdBy: asString(payload.createdBy),
    createdByName: asString(payload.createdByName),
    updatedBy: asString(payload.updatedBy),
    updatedByName: asString(payload.updatedByName),
    revision: asNumber(payload.revision),
    createdAt: asString(payload.createdAt, timestamp),
    updatedAt: timestamp,
    deleted,
  }
}

/**
 * Объект из `payload` журнала по классу и операции. `null`, если данных не хватает
 * или тип не поддержан (`ENTITY` из журналов прежней версии — `sync.md` §3.1).
 */
export function objectFromPayload(
  campaignId: string,
  objectClass: ObjectClass,
  payload: Record<string, unknown>,
  op: ChangeOp,
): LocalObject | null {
  const id = asOptionalString(payload.id)
  if (!id) return null
  const timestamp = asString(payload.updatedAt, nowIso())
  const deleted = op === 'DELETE' || asBoolean(payload.deleted)
  const common = commonPayload(campaignId, payload, id, timestamp, deleted)

  if (objectClass === 'HERO') {
    const dto: HeroDto = {
      ...common,
      race: asOptionalString(payload.race),
      klass: asOptionalString(payload.klass),
      gender: asOptionalString(payload.gender),
      playerUserId: asOptionalString(payload.playerUserId),
      playerName: asOptionalString(payload.playerName),
      items: deleted ? [] : asHeroItems(payload.items),
    }
    return objectFromDto('HERO', dto)
  }
  if (objectClass === 'CHARACTER') {
    const dto: CharacterDto = {
      ...common,
      race: asOptionalString(payload.race),
      klass: asOptionalString(payload.klass),
      gender: asOptionalString(payload.gender),
      shortDescription: asOptionalString(payload.shortDescription),
    }
    return objectFromDto('CHARACTER', dto)
  }
  if (objectClass === 'ITEM') {
    const dto: ItemDto = {
      ...common,
      itemType: asOptionalString(payload.itemType),
      ownerName: asOptionalString(payload.ownerName),
      ownerHeroId: asOptionalString(payload.ownerHeroId),
      ownerHeroName: asOptionalString(payload.ownerHeroName),
      ownerCharacterId: asOptionalString(payload.ownerCharacterId),
      ownerCharacterName: asOptionalString(payload.ownerCharacterName),
    }
    return objectFromDto('ITEM', dto)
  }
  const dto: LocationDto = { ...common, locationType: asOptionalString(payload.locationType) }
  return objectFromDto('LOCATION', dto)
}

function isRetellingStatus(value: unknown): value is RetellingStatus {
  return value === 'PUBLISHED' || value === 'DRAFT'
}

export function retellingFromPayload(
  campaignId: string,
  payload: Record<string, unknown>,
  op: ChangeOp,
): LocalRetelling | null {
  const id = asOptionalString(payload.id)
  if (!id) return null
  const timestamp = asString(payload.updatedAt, nowIso())
  const deleted = op === 'DELETE' || asBoolean(payload.deleted)
  const dto = {
    id,
    campaignId,
    clientId: asOptionalString(payload.clientId),
    title: asString(payload.title),
    body: asString(payload.body),
    authorEntityId: asOptionalString(payload.authorEntityId),
    authorEntityName: asOptionalString(payload.authorEntityName),
    sessionDate: asOptionalString(payload.sessionDate),
    status: isRetellingStatus(payload.status) ? payload.status : ('PUBLISHED' as RetellingStatus),
    links: asLinks(payload.links),
    revision: asNumber(payload.revision),
    createdBy: asString(payload.createdBy),
    createdByName: asString(payload.createdByName),
    updatedBy: asString(payload.updatedBy),
    updatedByName: asString(payload.updatedByName),
    createdAt: asString(payload.createdAt, timestamp),
    updatedAt: timestamp,
    deleted,
  }
  return retellingFromDto(dto)
}

function isMemberRole(value: unknown): value is MemberRole {
  return typeof value === 'string' && (MEMBER_ROLES as readonly string[]).includes(value)
}

function isMemberStatus(value: unknown): value is MemberStatus {
  return typeof value === 'string' && (MEMBER_STATUSES as readonly string[]).includes(value)
}

export function memberFromPayload(
  campaignId: string,
  payload: Record<string, unknown>,
  op: ChangeOp,
): MemberRecord | null {
  const id = asOptionalString(payload.id)
  if (!id) return null
  if (op === 'DELETE' || asBoolean(payload.deleted)) return null
  const dto: MemberDto = {
    id,
    userId: asString(payload.userId),
    displayName: asString(payload.displayName),
    email: asString(payload.email),
    role: isMemberRole(payload.role) ? payload.role : 'VIEWER',
    linkedHeroId: asOptionalString(payload.linkedHeroId),
    linkedHeroName: asOptionalString(payload.linkedHeroName),
    status: isMemberStatus(payload.status) ? payload.status : 'ACTIVE',
    createdAt: asString(payload.createdAt, nowIso()),
  }
  return memberFromDto(campaignId, dto)
}
