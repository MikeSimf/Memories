/**
 * Типы HTTP API DnD Memory v1.
 *
 * Модуль описывает модели из `docs/api.md` и `docs/sync.md` дословно:
 * имена полей и значения перечислений совпадают с контрактом сервера.
 * Здесь нет ни логики, ни зависимостей — только типы и словари значений.
 */

/* ------------------------------------------------------------------ */
/* 2. Перечисления                                                     */
/* ------------------------------------------------------------------ */

/** `EntityType` — герои, НПС, локации, предметы. */
export type EntityType = 'HERO' | 'NPC' | 'LOCATION' | 'ITEM'

/** `MemberRole` — роль участника кампании. */
export type MemberRole = 'OWNER' | 'EDITOR_MASTER' | 'EDITOR_HERO' | 'VIEWER'

/** `MemberStatus` — жизненный цикл участника. */
export type MemberStatus = 'INVITED' | 'ACTIVE' | 'REMOVED'

/** `RetellingStatus` — `DRAFT` живёт только локально, на сервер уходит только `PUBLISHED`. */
export type RetellingStatus = 'DRAFT' | 'PUBLISHED'

/** `ChangeOp` — операция в журнале изменений. */
export type ChangeOp = 'UPSERT' | 'DELETE'

/** `ObjectType` — тип объекта в журнале синхронизации. */
export type ObjectType = 'ENTITY' | 'RETELLING' | 'CAMPAIGN' | 'MEMBER'

/**
 * `InviteMode` — вид QR-кода (`api.md` §5, §3.1).
 * `MEMBER` — приглашение человеку, `DEVICE` — регистрация устройства.
 */
export type InviteMode = 'MEMBER' | 'DEVICE'

/** Плоский список прав текущего пользователя в кампании (`myPermissions`). */
export type Permission =
  | 'campaign.manage'
  | 'members.manage'
  | 'invite.create'
  | 'entity.read'
  | 'entity.write'
  | 'entity.write.own'
  | 'hero.write'
  | 'retelling.read'
  | 'retelling.write'
  | 'retelling.write.own'
  | 'retelling.publish'

/** Коды ошибок API (`error.code`). */
export type ApiErrorCode =
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'validation_failed'
  | 'conflict'
  | 'draft_not_synced'
  | 'invite_invalid'
  | 'email_taken'
  | 'phone_taken'
  | 'rate_limited'
  | 'internal_error'
  | 'resync_required'
  | 'network_error'
  | 'unknown'

export const ENTITY_TYPES: readonly EntityType[] = ['HERO', 'NPC', 'LOCATION', 'ITEM']

export const MEMBER_ROLES: readonly MemberRole[] = ['OWNER', 'EDITOR_MASTER', 'EDITOR_HERO', 'VIEWER']

export const MEMBER_STATUSES: readonly MemberStatus[] = ['INVITED', 'ACTIVE', 'REMOVED']

export const RETELLING_STATUSES: readonly RetellingStatus[] = ['DRAFT', 'PUBLISHED']

export const CHANGE_OPS: readonly ChangeOp[] = ['UPSERT', 'DELETE']

export const OBJECT_TYPES: readonly ObjectType[] = ['ENTITY', 'RETELLING', 'CAMPAIGN', 'MEMBER']

export const INVITE_MODES: readonly InviteMode[] = ['MEMBER', 'DEVICE']

export const PERMISSIONS: readonly Permission[] = [
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
]

export const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
  HERO: 'Герой',
  NPC: 'НПС',
  LOCATION: 'Локация',
  ITEM: 'Предмет',
}

export const ENTITY_TYPE_PLURALS: Record<EntityType, string> = {
  HERO: 'Герои',
  NPC: 'Персонажи',
  LOCATION: 'Локации',
  ITEM: 'Предметы',
}

export const MEMBER_ROLE_LABELS: Record<MemberRole, string> = {
  OWNER: 'Владелец',
  EDITOR_MASTER: 'Мастер',
  EDITOR_HERO: 'Игрок',
  VIEWER: 'Наблюдатель',
}

export const MEMBER_STATUS_LABELS: Record<MemberStatus, string> = {
  INVITED: 'Приглашён',
  ACTIVE: 'Активен',
  REMOVED: 'Удалён',
}

export const RETELLING_STATUS_LABELS: Record<RetellingStatus, string> = {
  DRAFT: 'Черновик',
  PUBLISHED: 'Опубликован',
}

/** Человекочитаемые сообщения по кодам ошибок API. */
export const API_ERROR_MESSAGES: Record<ApiErrorCode, string> = {
  unauthorized: 'Нужно войти заново',
  forbidden: 'Недостаточно прав',
  not_found: 'Не найдено',
  validation_failed: 'Проверьте заполненные поля',
  conflict: 'Конфликт версий',
  draft_not_synced: 'Черновик хранится локально и не отправляется на сервер',
  invite_invalid: 'Приглашение недействительно или просрочено',
  email_taken: 'Эта почта уже занята',
  phone_taken: 'Этот телефон уже занят',
  rate_limited: 'Слишком много запросов, попробуйте позже',
  internal_error: 'Ошибка на сервере',
  resync_required: 'Требуется полная перезагрузка данных кампании',
  network_error: 'Нет связи с сервером',
  unknown: 'Неизвестная ошибка',
}

/* ------------------------------------------------------------------ */
/* 1. Общие правила: ошибка                                            */
/* ------------------------------------------------------------------ */

export interface ApiErrorBody {
  error: {
    code: ApiErrorCode | string
    message: string
    details?: Record<string, unknown>
  }
}

/* ------------------------------------------------------------------ */
/* 3. Аутентификация                                                   */
/* ------------------------------------------------------------------ */

export interface UserDto {
  id: string
  /** Почта может быть не задана: аккаунт, созданный по QR устройства, живёт без неё. */
  email: string | null
  displayName: string
  avatarUrl: string | null
  /** Телефон в формате `+7XXXXXXXXXX`, если указан (`api.md` §3). */
  phone: string | null
  /** `false` у аккаунта, созданного по QR устройства: пароль ещё не задан. */
  hasPassword: boolean
}

export interface AuthResponse {
  user: UserDto
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface RefreshResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}

/** `PATCH /api/me`: имя, аватар и контакты для входа в веб-версию (`api.md` §3). */
export interface PatchMeRequest {
  displayName?: string
  avatarFileId?: string
  email?: string
  /** Пустая строка очищает телефон; сервер нормализует к `+7XXXXXXXXXX`. */
  phone?: string
}

/**
 * `POST /api/me/password`: текущий пароль обязателен, только если он уже задан
 * (`hasPassword: true`).
 */
export interface SetPasswordRequest {
  currentPassword?: string
  newPassword: string
}

export interface OAuthProviderInfo {
  enabled: boolean
  authorizeUrl: string
}

export type OAuthProvider = 'yandex' | 'vk'

export type OAuthProvidersResponse = Record<OAuthProvider, OAuthProviderInfo>

export interface OAuthCallbackRequest {
  code: string
  redirectUri: string
  state: string
}

/* ------------------------------------------------------------------ */
/* 4. Кампании                                                         */
/* ------------------------------------------------------------------ */

export interface CampaignListItem {
  id: string
  title: string
  description: string
  myRole: MemberRole
  memberCount: number
  entityCount: number
  retellingCount: number
  latestRevision: number
  updatedAt: string
}

export interface CampaignDto {
  id: string
  title: string
  description: string
  myRole: MemberRole
  myPermissions: Permission[]
  memberCount?: number
  entityCount?: number
  retellingCount?: number
  latestRevision?: number
  createdBy?: string
  createdAt?: string
  updatedAt: string
}

export interface CreateCampaignRequest {
  title: string
  description?: string
}

export interface PatchCampaignRequest {
  title?: string
  description?: string
}

/* ------------------------------------------------------------------ */
/* 5. Участники и приглашения                                          */
/* ------------------------------------------------------------------ */

export interface MemberDto {
  id: string
  userId: string
  displayName: string
  email: string
  role: MemberRole
  linkedEntityId: string | null
  linkedEntityName: string | null
  status: MemberStatus
  createdAt: string
}

export interface AddMemberRequest {
  email: string
  role: MemberRole
  linkedEntityId?: string | null
}

export interface PatchMemberRequest {
  role?: MemberRole
  linkedEntityId?: string | null
}

export interface AddMemberResponse {
  member: MemberDto
  /** Заполняется, если пользователя с такой почтой ещё нет: создано приглашение. */
  invite?: InviteDto | null
}

export interface InviteDto {
  id: string
  token: string
  url: string
  deepLink: string
  qrPngBase64: string
  role: MemberRole
  /** `MEMBER` — приглашение человеку, `DEVICE` — QR для регистрации устройства. */
  mode: InviteMode
  /** Имя, которое получит пользователь при регистрации устройства. */
  displayName: string | null
  /** Подпись устройства: «Телефон Ани». */
  label: string | null
  linkedEntityId: string | null
  linkedEntityName?: string | null
  expiresAt: string
  maxUses: number
  uses: number
  revoked?: boolean
  createdAt?: string
}

export interface CreateInviteRequest {
  role: MemberRole
  linkedEntityId?: string | null
  expiresInHours: number
  maxUses: number
}

export interface InvitePreviewDto {
  valid: boolean
  campaignId: string
  campaignTitle: string
  role: MemberRole
  linkedEntityId: string | null
  linkedEntityName: string | null
  /** Режим QR: для `DEVICE` браузер не принимает приглашение, а предлагает приложение. */
  mode: InviteMode
  displayName: string | null
  expiresAt: string
}

export interface AcceptInviteResponse {
  campaignId: string
  memberId: string
  role: MemberRole
  /** Сервер отдаёт их дополнительно — удобно для приветствия после принятия. */
  campaignTitle?: string
  linkedEntityId?: string | null
}

/* ------------------------------------------------------------------ */
/* 3.1. Регистрация устройства по QR                                   */
/* ------------------------------------------------------------------ */

/** `POST /api/campaigns/{id}/devices/enrollments` — тело шага админа. */
export interface CreateEnrollmentRequest {
  displayName: string
  role: MemberRole
  linkedEntityId?: string | null
  /** По умолчанию 24 часа, в отличие от недели у приглашения участника. */
  expiresInHours?: number
  maxUses?: number
  /** Подпись устройства: «Телефон Ани». */
  label?: string
}

/** `POST /api/devices/enroll` — тело шага телефона (без авторизации). */
export interface EnrollRequest {
  token: string
  label?: string
  platform?: string
}

/** Ответ `POST /api/devices/enroll`: сессия устройства и созданный аккаунт. */
export interface EnrollResponse {
  user: UserDto
  accessToken: string
  refreshToken: string
  expiresIn: number
  deviceId: string
  campaignId: string
  campaignTitle: string
  myRole: MemberRole
}

/** Элемент `GET /api/campaigns/{id}/devices`. */
export interface DeviceDto {
  id: string
  userId: string
  displayName: string
  label: string | null
  platform: string | null
  campaignId: string
  revoked: boolean
  createdAt: string
  lastSeenAt: string | null
}

/* ------------------------------------------------------------------ */
/* 6. Объекты (герои, НПС, локации, предметы)                          */
/* ------------------------------------------------------------------ */

/** Атрибуты объекта: известные ключи типизированы, остальные допускаются контрактом. */
export interface EntityAttrs {
  [key: string]: unknown
}

export type HeroStatus = 'alive' | 'dead' | 'retired'
export type NpcStatus = 'alive' | 'dead' | 'unknown'
export type LocationKind = 'city' | 'dungeon' | 'wild' | 'building'
export type ItemKind = 'weapon' | 'armor' | 'potion' | 'treasure' | 'quest'

export interface HeroAttrs extends EntityAttrs {
  playerName?: string
  race?: string
  klass?: string
  level?: number
  hp?: number
  ac?: number
  status?: HeroStatus
}

export interface NpcAttrs extends EntityAttrs {
  faction?: string
  status?: NpcStatus
  locationEntityId?: string
}

export interface LocationAttrs extends EntityAttrs {
  region?: string
  kind?: LocationKind
  parentEntityId?: string
}

export interface ItemAttrs extends EntityAttrs {
  kind?: ItemKind
  ownerEntityId?: string
  locationEntityId?: string
  quantity?: number
  rarity?: string
}

/** Ссылка `[[…]]`, разобранная сервером (или клиентом в офлайне). */
export interface EntityLinkDto {
  targetId: string | null
  label: string
  resolved: boolean
}

export interface EntityDto {
  id: string
  campaignId: string
  /** UUID, сгенерированный клиентом: идемпотентность офлайн-создания. */
  clientId: string
  type: EntityType
  name: string
  summary: string
  description: string
  attrs: EntityAttrs
  tags: string[]
  imageFileId: string | null
  links: EntityLinkDto[]
  createdBy: string
  updatedBy: string
  revision: number
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export interface CreateEntityRequest {
  id?: string
  clientId: string
  type: EntityType
  name: string
  summary?: string
  description?: string
  attrs?: EntityAttrs
  tags?: string[]
  imageFileId?: string | null
}

export interface PatchEntityRequest {
  name?: string
  summary?: string
  description?: string
  attrs?: EntityAttrs
  tags?: string[]
  imageFileId?: string | null
}

export interface EntityListQuery {
  /**
   * Фильтр по видам объектов (`api.md` §6): `?types=HERO,NPC`. Пусто — все виды.
   * Вкладка «Персонажи» запрашивает `HERO,NPC`, «Локации» — `LOCATION`,
   * «Предметы» — `ITEM`.
   */
  types?: EntityType[]
  q?: string
  updatedSince?: string
  limit?: number
  offset?: number
}

export interface PageDto<T> {
  items: T[]
  total?: number
  limit?: number
  offset?: number
}

/**
 * Обратная ссылка на объект (`GET .../entities/{id}/backlinks`).
 * Форма ответа в `api.md` не детализирована — описано назначение эндпоинта.
 */
export interface BacklinkDto {
  id: string
  type: EntityType
  name: string
  summary?: string
  label?: string
  updatedAt?: string
}

/* ------------------------------------------------------------------ */
/* 7. Пересказы                                                        */
/* ------------------------------------------------------------------ */

export interface RetellingDto {
  id: string
  campaignId: string
  clientId: string
  title: string
  body: string
  authorEntityId: string | null
  authorEntityName: string | null
  /** Дата сессии в формате `YYYY-MM-DD`. */
  sessionDate: string | null
  status: RetellingStatus
  links: EntityLinkDto[]
  revision: number
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export interface CreateRetellingRequest {
  id?: string
  clientId: string
  title: string
  body?: string
  authorEntityId?: string | null
  sessionDate?: string | null
  status: 'PUBLISHED'
}

export interface PatchRetellingRequest {
  title?: string
  body?: string
  authorEntityId?: string | null
  sessionDate?: string | null
  status?: 'PUBLISHED'
}

export interface RetellingListQuery {
  q?: string
  authorEntityId?: string
  limit?: number
  offset?: number
}

/* ------------------------------------------------------------------ */
/* 8. Поиск                                                           */
/* ------------------------------------------------------------------ */

export type SearchKind = 'ENTITY' | 'RETELLING'

export interface SearchResultItem {
  kind: SearchKind
  id: string
  type: EntityType | null
  title: string
  /** Подсвеченный фрагмент: сервер отдаёт `<b>` внутри текста. */
  snippet: string
  revision: number
  updatedAt: string
}

export interface SearchResponse {
  total: number
  items: SearchResultItem[]
}

export interface SearchQuery {
  q: string
  types?: string
  limit?: number
}

/* ------------------------------------------------------------------ */
/* 9. Синхронизация (см. docs/sync.md)                                 */
/* ------------------------------------------------------------------ */

export interface SyncPushChange {
  clientChangeId: string
  objectType: ObjectType
  objectId: string
  op: ChangeOp
  baseRevision: number | null
  payload: Record<string, unknown>
}

export interface SyncPushRequest {
  /** Идентификатор установки приложения. */
  clientId: string
  changes: SyncPushChange[]
}

export type SyncApplyStatus = 'APPLIED' | 'CONFLICT_RESOLVED' | 'REJECTED'

export type SyncConflictReason = 'stale_base' | 'forbidden' | (string & {})

export interface SyncConflictDto {
  reason: SyncConflictReason
  serverRevision?: number
  server?: Record<string, unknown> | null
}

export interface SyncPushResult {
  clientChangeId: string
  objectId: string
  revision: number
  status: SyncApplyStatus
  conflict: SyncConflictDto | null
}

export interface SyncPushResponse {
  latestRevision: number
  results: SyncPushResult[]
}

export interface SyncChangeLogEntry {
  revision: number
  objectType: ObjectType
  objectId: string
  op: ChangeOp
  payload: Record<string, unknown> | null
  actorUserId: string | null
  clientChangeId: string | null
  createdAt: string
}

export interface SyncPullResponse {
  since: number
  latestRevision: number
  hasMore: boolean
  changes: SyncChangeLogEntry[]
}

export interface SyncSnapshotResponse {
  latestRevision: number
  nextCursor: string | null
  entities: EntityDto[]
  retellings: RetellingDto[]
  members: MemberDto[]
  campaign: CampaignDto
}

export interface SyncStateDto {
  latestRevision: number
  entityCount: number
  retellingCount: number
  memberCount: number
  /** Минимальная сохранённая ревизия журнала (журнал компактится), `sync.md` §6. */
  minRevision?: number
}

/* ------------------------------------------------------------------ */
/* 10. Файлы                                                          */
/* ------------------------------------------------------------------ */

export interface FileDto {
  id: string
  url: string
  contentType: string
  size: number
}

/* ------------------------------------------------------------------ */
/* 11. Служебные                                                      */
/* ------------------------------------------------------------------ */

export interface HealthDto {
  status: string
  version: string
  time: string
}

export interface VersionDto {
  version: string
  buildTime: string
  profile: string
}
