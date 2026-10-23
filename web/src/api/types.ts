/**
 * Типы HTTP API DnD Memory v2.
 *
 * Модуль описывает модели из `docs/api.md` и `docs/sync.md` дословно:
 * имена полей и значения перечислений совпадают с контрактом сервера.
 * Здесь нет ни логики, ни зависимостей — только типы и словари значений.
 */

/* ------------------------------------------------------------------ */
/* 2. Перечисления                                                     */
/* ------------------------------------------------------------------ */

/**
 * `ObjectClass` — четыре класса справочника (`api.md` §6). Вместо одной
 * таблицы `entities` у каждого класса свои поля и свои эндпоинты.
 */
export type ObjectClass = 'HERO' | 'CHARACTER' | 'LOCATION' | 'ITEM'

/** `MemberRole` — роль участника кампании. */
export type MemberRole = 'OWNER' | 'EDITOR_MASTER' | 'EDITOR_HERO' | 'VIEWER'

/** `MemberStatus` — жизненный цикл участника. */
export type MemberStatus = 'INVITED' | 'ACTIVE' | 'REMOVED'

/** `RetellingStatus` — `DRAFT` живёт только локально, на сервер уходит только `PUBLISHED`. */
export type RetellingStatus = 'DRAFT' | 'PUBLISHED'

/** `ChangeOp` — операция в журнале изменений. */
export type ChangeOp = 'UPSERT' | 'DELETE'

/** `ObjectType` — тип объекта в журнале синхронизации (`api.md` §2). */
export type ObjectType = ObjectClass | 'RETELLING' | 'CAMPAIGN' | 'MEMBER' | 'MATERIAL'

/** Плоский список прав текущего пользователя в кампании (`myPermissions`). */
export type Permission =
  | 'campaign.manage'
  | 'campaign.delete'
  | 'members.manage'
  | 'invite.create'
  | 'entity.read'
  | 'entity.write'
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
  | 'login_taken'
  | 'phone_taken'
  | 'reset_invalid'
  | 'rate_limited'
  | 'internal_error'
  | 'resync_required'
  | 'network_error'
  | 'unknown'

export const OBJECT_CLASSES: readonly ObjectClass[] = ['HERO', 'CHARACTER', 'LOCATION', 'ITEM']

export const MEMBER_ROLES: readonly MemberRole[] = ['OWNER', 'EDITOR_MASTER', 'EDITOR_HERO', 'VIEWER']

export const MEMBER_STATUSES: readonly MemberStatus[] = ['INVITED', 'ACTIVE', 'REMOVED']

export const RETELLING_STATUSES: readonly RetellingStatus[] = ['DRAFT', 'PUBLISHED']

export const CHANGE_OPS: readonly ChangeOp[] = ['UPSERT', 'DELETE']

export const OBJECT_TYPES: readonly ObjectType[] = [
  'HERO',
  'CHARACTER',
  'LOCATION',
  'ITEM',
  'RETELLING',
  'CAMPAIGN',
  'MEMBER',
]

export const PERMISSIONS: readonly Permission[] = [
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
]

export const OBJECT_CLASS_LABELS: Record<ObjectClass, string> = {
  HERO: 'Герой',
  CHARACTER: 'Персонаж',
  LOCATION: 'Локация',
  ITEM: 'Предмет',
}

export const OBJECT_CLASS_PLURALS: Record<ObjectClass, string> = {
  HERO: 'Герои',
  CHARACTER: 'Персонажи',
  LOCATION: 'Локации',
  ITEM: 'Предметы',
}

/** Сегмент адреса эндпоинтов класса (`api.md` §6). */
export const OBJECT_CLASS_PATHS: Record<ObjectClass, string> = {
  HERO: 'heroes',
  CHARACTER: 'characters',
  LOCATION: 'locations',
  ITEM: 'items',
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
  invite_invalid: 'Приглашение уже использовано или просрочено',
  email_taken: 'Эта почта уже занята',
  login_taken: 'Этот логин уже занят',
  phone_taken: 'Этот телефон уже занят',
  reset_invalid: 'Ссылка сброса пароля недействительна или просрочена',
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

export interface PageDto<T> {
  items: T[]
  total: number
}

/* ------------------------------------------------------------------ */
/* 3. Аутентификация                                                   */
/* ------------------------------------------------------------------ */

export interface UserDto {
  id: string
  /** Логин для входа: 3–32 символа из `a–z`, `0–9`, `_`, `.`, `-`. */
  login: string
  /** Почта обязательна: контакт для сброса пароля. */
  email: string
  emailVerified: boolean
  displayName: string
  avatarUrl: string | null
  avatarFileId?: string | null
  /** Телефон в формате `+7XXXXXXXXXX`, если указан (`api.md` §3). */
  phone: string | null
  /** Пароль есть всегда: вход только по логину и паролю. */
  hasPassword: boolean
  createdAt?: string
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
  login: string
  email: string
  password: string
  displayName: string
  /** Токен приглашения из QR: регистрация и присоединение одним запросом. */
  inviteToken?: string
}

export interface LoginRequest {
  login: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}

export interface PasswordResetRequestBody {
  email: string
}

export interface PasswordResetConfirmBody {
  token: string
  newPassword: string
}

/** `PATCH /api/me`: имя, аватар и контакты (`api.md` §3). */
export interface PatchMeRequest {
  displayName?: string
  avatarFileId?: string
  email?: string
  /** Пустая строка очищает телефон; сервер нормализует к `+7XXXXXXXXXX`. */
  phone?: string
}

/** `POST /api/me/password`: текущий пароль обязателен — пароль есть всегда. */
export interface SetPasswordRequest {
  currentPassword: string
  newPassword: string
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
  /** Сумма объектов всех четырёх классов. */
  entityCount: number
  retellingCount: number
  latestRevision: number
  updatedAt: string
}

/** Краткий повествование рядом со счётчиками в карточке кампании (`api.md` §4). */
export interface RetellingBriefDto {
  id: string
  title: string
  sessionDate: string | null
  revision: number
  updatedAt: string
}

export interface CampaignDto {
  id: string
  title: string
  description: string
  ownerUserId: string | null
  myRole: MemberRole
  myPermissions: Permission[]
  memberCount: number
  entityCount: number
  retellingCount: number
  latestRevision: number
  /** Краткий список участников — карточка кампании открывается одним запросом. */
  members: MemberDto[]
  /** Краткий список повествований. */
  retellings: RetellingBriefDto[]
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
  /** Герой, закреплённый за участником (`roles.md` §1). */
  linkedHeroId: string | null
  linkedHeroName: string | null
  status: MemberStatus
  createdAt: string
}

export interface AddMemberRequest {
  email: string
  role: MemberRole
  linkedHeroId?: string | null
}

export interface PatchMemberRequest {
  role?: MemberRole
  linkedHeroId?: string | null
}

export interface AddMemberResponse {
  member: MemberDto
  /** Заполняется, если пользователя с такой почтой ещё нет: создано приглашение. */
  invite?: InviteDto | null
  /** Пояснение сервера (например, что участник добавлен сразу). */
  note?: string | null
}

export interface InviteDto {
  id: string
  token: string
  url: string
  deepLink: string
  qrPngBase64: string
  role: MemberRole
  linkedHeroId: string | null
  linkedHeroName: string | null
  expiresAt: string
  /** Всегда 1: приглашение одноразовое (`api.md` §5). */
  maxUses: number
  uses: number
  revoked: boolean
  /** `false` — приглашение погашено или просрочено. */
  valid: boolean
  createdAt?: string
}

export interface CreateInviteRequest {
  role: MemberRole
  linkedHeroId?: string | null
  expiresInHours?: number
}

export interface InvitePreviewDto {
  valid: boolean
  campaignId: string
  campaignTitle: string
  role: MemberRole
  linkedHeroId: string | null
  linkedHeroName: string | null
  expiresAt: string
}

export interface AcceptInviteResponse {
  campaignId: string
  campaignTitle: string
  memberId: string
  role: MemberRole
  linkedHeroId: string | null
}

/* ------------------------------------------------------------------ */
/* 6. Объекты: четыре класса                                           */
/* ------------------------------------------------------------------ */

/** Ссылка `[[…]]`, разобранная сервером (или клиентом в офлайне). */
export interface EntityLinkDto {
  targetId: string | null
  label: string
  resolved: boolean
}

/** Общие поля ответа всех четырёх классов (`api.md` §6). */
export interface ObjectDto {
  id: string
  campaignId: string
  name: string
  description: string
  imageFileId: string | null
  /** Аватар: отдельная картинка, не из галереи. */
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
}

/** Предмет в котомке героя (`hero_items`). */
export interface HeroItemDto {
  id: string
  name: string
  /** Заполнен, если название нашлось среди предметов кампании. */
  itemId: string | null
  quantity: number | null
  notes: string | null
}

/** Герой партии: раса, класс, игрок и список предметов. */
export interface HeroDto extends ObjectDto {
  race: string | null
  klass: string | null
  gender: string | null
  playerUserId: string | null
  playerName: string | null
  items: HeroItemDto[]
}

/** Персонаж (НПС): раса и краткое описание — по ним тоже идёт поиск. */
export interface CharacterDto extends ObjectDto {
  race: string | null
  /** Класс персонажа из справочника character_classes. */
  klass: string | null
  gender: string | null
  shortDescription: string | null
}

/** Локация: тип из справочника location_types. */
export interface LocationDto extends ObjectDto {
  locationType: string | null
}

/** Предмет: владелец как написано в мире и ссылка не более чем на одного героя/НПС. */
export interface ItemDto extends ObjectDto {
  /** Тип из справочника item_types. */
  itemType: string | null
  ownerName: string | null
  ownerHeroId: string | null
  ownerHeroName: string | null
  ownerCharacterId: string | null
  ownerCharacterName: string | null
}

export type CampaignObjectDto = HeroDto | CharacterDto | LocationDto | ItemDto

/** Соответствие «класс → его DTO» — для типизированных обобщённых функций. */
export interface ObjectDtoMap {
  HERO: HeroDto
  CHARACTER: CharacterDto
  LOCATION: LocationDto
  ITEM: ItemDto
}

/** DTO конкретного класса — по значению `ObjectClass`. */
export type ObjectDtoOf<C extends ObjectClass> = ObjectDtoMap[C]

/** Запросы создания и правки по классам — то же соответствие для форм. */
export interface CreateObjectMap {
  HERO: CreateHeroRequest
  CHARACTER: CreateCharacterRequest
  LOCATION: CreateLocationRequest
  ITEM: CreateItemRequest
}

export interface UpdateObjectMap {
  HERO: UpdateHeroRequest
  CHARACTER: UpdateCharacterRequest
  LOCATION: UpdateLocationRequest
  ITEM: UpdateItemRequest
}

export interface HeroItemRequest {
  name: string
  itemId?: string | null
  quantity?: number | null
  notes?: string | null
}

export interface CreateHeroRequest {
  id?: string
  name: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
  race?: string | null
  klass?: string | null
  gender?: string | null
  playerUserId?: string | null
  items?: HeroItemRequest[]
}

export interface UpdateHeroRequest {
  name?: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
  race?: string | null
  klass?: string | null
  gender?: string | null
  playerUserId?: string | null
  items?: HeroItemRequest[]
}

export interface CreateCharacterRequest {
  id?: string
  name: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
  race?: string | null
  gender?: string | null
  shortDescription?: string | null
}

export interface UpdateCharacterRequest {
  name?: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
  race?: string | null
  gender?: string | null
  shortDescription?: string | null
}

export interface CreateLocationRequest {
  id?: string
  name: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
}

export interface UpdateLocationRequest {
  name?: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
}

export interface CreateItemRequest {
  id?: string
  name: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
  ownerName?: string | null
  ownerHeroId?: string | null
  ownerCharacterId?: string | null
}

export interface UpdateItemRequest {
  name?: string
  description?: string
  imageFileId?: string | null
  avatarFileId?: string | null
  ownerName?: string | null
  ownerHeroId?: string | null
  ownerCharacterId?: string | null
}

export type CreateObjectRequest =
  | CreateHeroRequest
  | CreateCharacterRequest
  | CreateLocationRequest
  | CreateItemRequest

export type UpdateObjectRequest =
  | UpdateHeroRequest
  | UpdateCharacterRequest
  | UpdateLocationRequest
  | UpdateItemRequest

export interface ObjectListQuery {
  q?: string
  updatedSince?: string
  limit?: number
  offset?: number
}

/* ------------------------------------------------------------------ */
/* 7. Повествования                                                        */
/* ------------------------------------------------------------------ */

export interface RetellingDto {
  id: string
  campaignId: string
  clientId: string | null
  title: string
  body: string
  /** Герой или персонаж, от лица которого ведётся повествование. */
  authorEntityId: string | null
  authorEntityName: string | null
  /** Дата сессии в формате `YYYY-MM-DD`. */
  sessionDate: string | null
  status: RetellingStatus
  links: EntityLinkDto[]
  revision: number
  createdBy: string
  createdByName: string
  updatedBy: string
  updatedByName: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export interface CreateRetellingRequest {
  id?: string
  clientId?: string
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

/** Что вернул поиск: любой из четырёх классов или повествование (`api.md` §8). */
export type SearchKind = ObjectClass | 'RETELLING'

export const SEARCH_KINDS: readonly SearchKind[] = [
  'HERO',
  'CHARACTER',
  'LOCATION',
  'ITEM',
  'RETELLING',
]

export const SEARCH_KIND_LABELS: Record<SearchKind, string> = {
  HERO: 'Герои',
  CHARACTER: 'Персонажи',
  LOCATION: 'Локации',
  ITEM: 'Предметы',
  RETELLING: 'Повествования',
}

export interface SearchResultItem {
  kind: SearchKind
  id: string
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
  /** Список видов через запятую: `?kinds=HERO,CHARACTER`. */
  kinds?: string
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
  /** Объект в форме DTO своего класса (`sync.md` §1). */
  payload: Record<string, unknown> | null
}

export interface SyncPushRequest {
  /** Идентификатор установки приложения. */
  clientId: string
  changes: SyncPushChange[]
}

export type SyncApplyStatus = 'APPLIED' | 'CONFLICT_RESOLVED' | 'REJECTED'

export type SyncConflictReason =
  | 'stale_base'
  | 'client_wins'
  | 'forbidden'
  | 'matched_by_name'
  | 'matched_by_name_and_race'
  | 'unsupported_type'
  | (string & {})

export interface SyncConflictDto {
  reason: SyncConflictReason
  serverRevision?: number
  server?: Record<string, unknown> | null
}

export interface SyncPushResult {
  clientChangeId: string
  objectId: string
  revision: number | null
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

/**
 * Полная выгрузка: страницы идут по порядку классов — герои, персонажи,
 * локации, предметы, повествования (`sync.md` §3.3). За страницу отдаётся один класс.
 */
export interface SyncSnapshotResponse {
  latestRevision: number
  nextCursor: string | null
  campaign: CampaignDto | null
  heroes: HeroDto[]
  characters: CharacterDto[]
  locations: LocationDto[]
  items: ItemDto[]
  materials: MaterialDto[]
  retellings: RetellingDto[]
  members: MemberDto[]
}

/** Файл объекта или материала: фото, карта, документ. */
export interface AttachmentDto {
  id: string
  name: string | null
  contentType: string | null
  size: number | null
}

/** Фото-материал кампании: картинка или документ плюс название и описание. */
export interface MaterialDto {
  id: string
  campaignId: string
  name: string
  description: string
  imageFileId: string | null
  avatarFileId: string | null
  files: AttachmentDto[] | null
  links: EntityLinkDto[] | null
  createdBy: string
  createdByName: string
  updatedBy: string
  updatedByName: string
  revision: number
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export interface SyncStateDto {
  latestRevision: number
  minRetainedRevision: number | null
  /** Сумма объектов всех четырёх классов. */
  entityCount: number
  heroCount: number
  characterCount: number
  locationCount: number
  itemCount: number
  retellingCount: number
  memberCount: number
  pendingConflicts: number
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

/** Одна запись истории версий (`GET /api/app/versions`). */
export interface AppVersionDto {
  version: string
  date: string
  notes: string
}

/** Данные обновления приложения для Android (`GET /api/app/latest`). */
export interface AppLatestDto {
  versionCode: number
  versionName: string
  minVersionCode: number
  /** Прямая ссылка на apk; на сайте используем свой путь `/dnd-memory.apk`. */
  url: string
  notes: string
}

export interface VersionDto {
  version: string
  buildTime: string
  profile: string
}
