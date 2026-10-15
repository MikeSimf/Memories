import type { MemberRole, Permission, RetellingDto } from '@/api/types'

/**
 * Локальное зеркало матрицы ролей из `docs/roles.md`.
 * Сервер всё равно проверяет права сам (`api.md` §2), но интерфейс должен
 * показывать только доступные действия, в том числе без сети.
 */
export const ROLE_PERMISSIONS: Record<MemberRole, readonly Permission[]> = {
  OWNER: [
    'campaign.manage',
    'members.manage',
    'invite.create',
    'entity.read',
    'entity.write',
    'entity.write.own',
    'hero.write',
    'retelling.read',
    'retelling.write',
    'retelling.write.own',
    'retelling.publish',
  ],
  EDITOR_MASTER: [
    'entity.read',
    'entity.write',
    'entity.write.own',
    'hero.write',
    'retelling.read',
    'retelling.write',
    'retelling.write.own',
    'retelling.publish',
  ],
  EDITOR_HERO: [
    'entity.read',
    'entity.write',
    'entity.write.own',
    'hero.write',
    'retelling.read',
    'retelling.write',
    'retelling.write.own',
  ],
  VIEWER: ['entity.read', 'retelling.read'],
}

export function permissionsForRole(role: MemberRole): Permission[] {
  return [...(ROLE_PERMISSIONS[role] ?? [])]
}

export function hasPermission(
  permissions: readonly string[] | null | undefined,
  permission: Permission,
): boolean {
  if (!permissions) return false
  return permissions.includes(permission)
}

export interface EntityPermissionContext {
  /** Тип объекта (`HERO` — герой партии). */
  type: string
  id: string
  createdBy: string
}

export interface RetellingPermissionContext {
  createdBy: string
}

export interface PermissionSubject {
  /** Идентификатор текущего пользователя. */
  userId: string | null
  permissions: readonly string[] | null | undefined
  /** Герой, привязанный к участнику (`linkedEntityId`) — `roles.md` §1. */
  linkedEntityId: string | null
}

/** Право писать объекты этого типа: НПС/локации/предметы — `entity.write`, герои — `hero.write`. */
export function canWriteEntityType(subject: PermissionSubject, type: string): boolean {
  if (type === 'HERO') {
    return hasPermission(subject.permissions, 'hero.write')
  }
  return hasPermission(subject.permissions, 'entity.write')
}

/** Создание конкретного объекта. Единственный герой `EDITOR_HERO` — свой (по привязке). */
export function canCreateEntity(subject: PermissionSubject, type: string): boolean {
  if (type === 'HERO') {
    return (
      hasPermission(subject.permissions, 'hero.write') ||
      hasPermission(subject.permissions, 'entity.write')
    )
  }
  return hasPermission(subject.permissions, 'entity.write')
}

/** Правка объекта: право на тип, авторство или «свой герой» (`roles.md` §2, правила 2 и 4). */
export function canEditEntity(
  subject: PermissionSubject,
  entity: EntityPermissionContext,
): boolean {
  if (hasPermission(subject.permissions, 'entity.write')) return true
  if (entity.type === 'HERO') {
    if (
      hasPermission(subject.permissions, 'hero.write') &&
      subject.linkedEntityId !== null &&
      subject.linkedEntityId === entity.id
    ) {
      return true
    }
  }
  if (hasPermission(subject.permissions, 'entity.write.own') && isAuthor(subject, entity.createdBy)) {
    return true
  }
  return false
}

export function canDeleteEntity(
  subject: PermissionSubject,
  entity: EntityPermissionContext,
): boolean {
  if (entity.type === 'HERO' && !hasPermission(subject.permissions, 'hero.write') && !hasPermission(subject.permissions, 'entity.write')) {
    return false
  }
  return canEditEntity(subject, entity)
}

function isAuthor(subject: PermissionSubject, createdBy: string): boolean {
  return subject.userId !== null && createdBy !== '' && subject.userId === createdBy
}

/** Можно ли создать пересказ: `retelling.write` (`roles.md` §2). */
export function canCreateRetelling(subject: PermissionSubject): boolean {
  return hasPermission(subject.permissions, 'retelling.write')
}

/**
 * Правка пересказа: автор — при `retelling.write.own`, чужой — только
 * при `retelling.publish` (`api.md` §7, `roles.md` §2, правило 3).
 */
export function canEditRetelling(
  subject: PermissionSubject,
  retelling: RetellingPermissionContext,
): boolean {
  if (hasPermission(subject.permissions, 'retelling.publish')) return true
  if (hasPermission(subject.permissions, 'retelling.write') && isAuthor(subject, retelling.createdBy)) {
    return true
  }
  return hasPermission(subject.permissions, 'retelling.write.own') && isAuthor(subject, retelling.createdBy)
}

export function canDeleteRetelling(
  subject: PermissionSubject,
  retelling: RetellingPermissionContext,
): boolean {
  if (hasPermission(subject.permissions, 'campaign.manage')) return true
  return isAuthor(subject, retelling.createdBy) && canEditRetelling(subject, retelling)
}

/**
 * Пересказ «от лица героя»: `EDITOR_HERO` может публиковать от лица своего героя.
 */
export function canUseAuthorEntity(subject: PermissionSubject, authorEntityId: string | null): boolean {
  if (authorEntityId === null) return true
  if (hasPermission(subject.permissions, 'retelling.publish')) return true
  if (hasPermission(subject.permissions, 'entity.write')) return true
  return subject.linkedEntityId === authorEntityId
}

export function isOwnRetelling(subject: PermissionSubject, retelling: RetellingDto): boolean {
  return isAuthor(subject, retelling.createdBy)
}
