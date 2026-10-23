import type { MemberRole, ObjectClass, Permission, RetellingDto } from '@/api/types'

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
    'hero.write',
    'retelling.read',
    'retelling.write',
    'retelling.write.own',
    'retelling.publish',
  ],
  // Героев ведёт только владелец кампании (`roles.md` §2, правило 2).
  EDITOR_MASTER: [
    'entity.read',
    'entity.write',
    'retelling.read',
    'retelling.write',
    'retelling.write.own',
    'retelling.publish',
  ],
  EDITOR_HERO: [
    'entity.read',
    'entity.write',
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

export interface RetellingPermissionContext {
  createdBy: string
}

export interface PermissionSubject {
  /** Идентификатор текущего пользователя. */
  userId: string | null
  permissions: readonly string[] | null | undefined
  /** Герой, привязанный к участнику (`linkedHeroId`) — `roles.md` §1. */
  linkedHeroId: string | null
  /** Роль участника — для подписей интерфейса. */
  role?: MemberRole | null
}

/**
 * Право писать объекты класса (`roles.md` §2): герои — `hero.write`, который есть
 * только у владельца; персонажи, локации и предметы — `entity.write`.
 */
export function canWriteClass(subject: PermissionSubject, objectClass: ObjectClass): boolean {
  if (objectClass === 'HERO') {
    return hasPermission(subject.permissions, 'hero.write')
  }
  return hasPermission(subject.permissions, 'entity.write')
}

/** Создание объекта класса — те же права, что и на правку. */
export function canCreateObject(subject: PermissionSubject, objectClass: ObjectClass): boolean {
  return canWriteClass(subject, objectClass)
}

export function canEditObject(subject: PermissionSubject, objectClass: ObjectClass): boolean {
  return canWriteClass(subject, objectClass)
}

export function canDeleteObject(subject: PermissionSubject, objectClass: ObjectClass): boolean {
  return canWriteClass(subject, objectClass)
}

/** Можно ли создать повествование: `retelling.write` (`roles.md` §2). */
export function canCreateRetelling(subject: PermissionSubject): boolean {
  return hasPermission(subject.permissions, 'retelling.write')
}

/**
 * Правка повествования: автор — при `retelling.write.own`, чужой — только
 * при `retelling.publish` (`api.md` §7, `roles.md` §2, правило 4).
 */
export function canEditRetelling(
  subject: PermissionSubject,
  retelling: RetellingPermissionContext,
): boolean {
  if (hasPermission(subject.permissions, 'retelling.publish')) return true
  if (
    hasPermission(subject.permissions, 'retelling.write.own') &&
    isAuthor(subject, retelling.createdBy)
  ) {
    return true
  }
  return hasPermission(subject.permissions, 'retelling.write') && isAuthor(subject, retelling.createdBy)
}

export function canDeleteRetelling(
  subject: PermissionSubject,
  retelling: RetellingPermissionContext,
): boolean {
  if (hasPermission(subject.permissions, 'campaign.manage')) return true
  return isAuthor(subject, retelling.createdBy) && canEditRetelling(subject, retelling)
}

/**
 * Повествование «от лица героя»: `EDITOR_HERO` может публиковать от лица своего героя.
 */
export function canUseAuthorEntity(subject: PermissionSubject, authorEntityId: string | null): boolean {
  if (authorEntityId === null) return true
  if (hasPermission(subject.permissions, 'retelling.publish')) return true
  if (hasPermission(subject.permissions, 'entity.write')) return true
  return subject.linkedHeroId === authorEntityId
}

export function isOwnRetelling(subject: PermissionSubject, retelling: RetellingDto): boolean {
  return isAuthor(subject, retelling.createdBy)
}

function isAuthor(subject: PermissionSubject, createdBy: string): boolean {
  return subject.userId !== null && createdBy !== '' && subject.userId === createdBy
}
