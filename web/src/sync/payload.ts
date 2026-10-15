import type {
  ChangeOp,
  EntityAttrs,
  EntityDto,
  EntityLinkDto,
  EntityType,
  MemberDto,
  MemberRole,
  MemberStatus,
  RetellingStatus,
} from '@/api/types'
import { ENTITY_TYPES, MEMBER_ROLES, MEMBER_STATUSES } from '@/api/types'
import { entityFromDto, memberFromDto, retellingFromDto } from '@/db'
import type { LocalEntity, LocalRetelling, MemberRecord } from '@/db/schema'
import { nowIso } from '@/db/schema'

/**
 * Разбор `payload` из журнала синхронизации (`sync.md` §1): сервер присылает
 * полное состояние объекта, для `DELETE` — надгробие `{ id, deleted, updatedAt }`.
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

function asBoolean(value: unknown, fallback = false): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string')
}

function asAttrs(value: unknown): EntityAttrs {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return {}
  return value as EntityAttrs
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

function isEntityType(value: unknown): value is EntityType {
  return typeof value === 'string' && (ENTITY_TYPES as readonly string[]).includes(value)
}

/** Объект из `payload`; `null`, если это надгробие или данные неполные. */
export function entityFromPayload(
  campaignId: string,
  payload: Record<string, unknown>,
  op: ChangeOp,
): LocalEntity | null {
  const id = asOptionalString(payload.id)
  if (!id) return null
  const timestamp = asString(payload.updatedAt, nowIso())
  if (op === 'DELETE' || asBoolean(payload.deleted)) {
    return {
      id,
      campaignId,
      clientId: asString(payload.clientId, id),
      type: isEntityType(payload.type) ? payload.type : 'NPC',
      name: asString(payload.name),
      summary: asString(payload.summary),
      description: asString(payload.description),
      attrs: asAttrs(payload.attrs),
      tags: asStringArray(payload.tags),
      imageFileId: asOptionalString(payload.imageFileId),
      links: asLinks(payload.links),
      createdBy: asString(payload.createdBy),
      updatedBy: asString(payload.updatedBy),
      revision: asNumber(payload.revision),
      createdAt: asString(payload.createdAt, timestamp),
      updatedAt: timestamp,
      deleted: true,
      dirty: 0,
      conflict: null,
    }
  }
  if (!isEntityType(payload.type)) return null
  const dto: EntityDto = {
    id,
    campaignId,
    clientId: asString(payload.clientId, id),
    type: payload.type,
    name: asString(payload.name),
    summary: asString(payload.summary),
    description: asString(payload.description),
    attrs: asAttrs(payload.attrs),
    tags: asStringArray(payload.tags),
    imageFileId: asOptionalString(payload.imageFileId),
    links: asLinks(payload.links),
    createdBy: asString(payload.createdBy),
    updatedBy: asString(payload.updatedBy),
    revision: asNumber(payload.revision),
    createdAt: asString(payload.createdAt, timestamp),
    updatedAt: timestamp,
    deleted: false,
  }
  return entityFromDto(dto)
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
  if (op === 'DELETE' || asBoolean(payload.deleted)) {
    return {
      id,
      campaignId,
      clientId: asString(payload.clientId, id),
      title: asString(payload.title),
      body: asString(payload.body),
      authorEntityId: asOptionalString(payload.authorEntityId),
      authorEntityName: asOptionalString(payload.authorEntityName),
      sessionDate: asOptionalString(payload.sessionDate),
      status: 'PUBLISHED',
      links: asLinks(payload.links),
      revision: asNumber(payload.revision),
      createdBy: asString(payload.createdBy),
      updatedBy: asString(payload.updatedBy),
      createdAt: asString(payload.createdAt, timestamp),
      updatedAt: timestamp,
      deleted: true,
      dirty: 0,
      conflict: null,
    }
  }
  const dto = {
    id,
    campaignId,
    clientId: asString(payload.clientId, id),
    title: asString(payload.title),
    body: asString(payload.body),
    authorEntityId: asOptionalString(payload.authorEntityId),
    authorEntityName: asOptionalString(payload.authorEntityName),
    sessionDate: asOptionalString(payload.sessionDate),
    status: isRetellingStatus(payload.status) ? payload.status : 'PUBLISHED',
    links: asLinks(payload.links),
    revision: asNumber(payload.revision),
    createdBy: asString(payload.createdBy),
    updatedBy: asString(payload.updatedBy),
    createdAt: asString(payload.createdAt, timestamp),
    updatedAt: timestamp,
    deleted: false,
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
    linkedEntityId: asOptionalString(payload.linkedEntityId),
    linkedEntityName: asOptionalString(payload.linkedEntityName),
    status: isMemberStatus(payload.status) ? payload.status : 'ACTIVE',
    createdAt: asString(payload.createdAt, nowIso()),
  }
  return memberFromDto(campaignId, dto)
}
