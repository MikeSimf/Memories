import { apiClient } from './client'
import { OBJECT_CLASS_PATHS } from './types'
import type {
  AcceptInviteResponse,
  AddMemberRequest,
  AddMemberResponse,
  AuthResponse,
  CampaignDto,
  CampaignListItem,
  CreateCampaignRequest,
  CreateInviteRequest,
  CreateObjectMap,
  CreateRetellingRequest,
  FileDto,
  HealthDto,
  MaterialDto,
  InviteDto,
  InvitePreviewDto,
  LoginRequest,
  LogoutRequest,
  MemberDto,
  ObjectClass,
  ObjectDtoOf,
  ObjectListQuery,
  PageDto,
  PatchCampaignRequest,
  PatchMeRequest,
  PatchMemberRequest,
  PatchRetellingRequest,
  PasswordResetConfirmBody,
  PasswordResetRequestBody,
  RefreshRequest,
  RefreshResponse,
  RegisterRequest,
  RetellingDto,
  RetellingListQuery,
  SearchKind,
  SearchResponse,
  SetPasswordRequest,
  SyncPullResponse,
  SyncPushRequest,
  SyncPushResponse,
  SyncSnapshotResponse,
  SyncStateDto,
  UpdateObjectMap,
  UserDto,
  VersionDto,
  AppLatestDto,
  AppVersionDto,
} from './types'

/** Список приходит страницей; «сырой» массив приводим к странице на всякий случай. */
function toPage<T>(value: PageDto<T> | T[] | null): PageDto<T> {
  if (Array.isArray(value)) {
    return { items: value, total: value.length }
  }
  if (!value || !Array.isArray(value.items)) {
    return { items: [], total: 0 }
  }
  return { items: value.items, total: typeof value.total === 'number' ? value.total : value.items.length }
}

function classPath(objectClass: ObjectClass): string {
  return OBJECT_CLASS_PATHS[objectClass]
}

/* ----------------------------- Аутентификация ----------------------------- */

export const authApi = {
  register(body: RegisterRequest): Promise<AuthResponse> {
    return apiClient.request<AuthResponse>({
      method: 'POST',
      path: '/api/auth/register',
      body,
      auth: false,
    })
  },

  login(body: LoginRequest): Promise<AuthResponse> {
    return apiClient.request<AuthResponse>({
      method: 'POST',
      path: '/api/auth/login',
      body,
      auth: false,
    })
  },

  refresh(body: RefreshRequest): Promise<RefreshResponse> {
    return apiClient.request<RefreshResponse>({
      method: 'POST',
      path: '/api/auth/refresh',
      body,
      auth: false,
      skipRefresh: true,
    })
  },

  async logout(refreshToken: string): Promise<void> {
    const body: LogoutRequest = { refreshToken }
    await apiClient.request<void>({
      method: 'POST',
      path: '/api/auth/logout',
      body,
      auth: false,
      skipRefresh: true,
    })
  },

  /**
   * `POST /api/auth/password-reset/request` (`api.md` §3): отвечает `202` всегда,
   * поэтому по ответу нельзя узнать, есть ли такой адрес. Ссылку пишет в лог сервер.
   */
  async requestPasswordReset(email: string): Promise<void> {
    const body: PasswordResetRequestBody = { email }
    await apiClient.request<void>({
      method: 'POST',
      path: '/api/auth/password-reset/request',
      body,
      auth: false,
      skipRefresh: true,
    })
  },

  /** `POST /api/auth/password-reset/confirm`: меняет пароль и гасит все сессии. */
  async confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    const body: PasswordResetConfirmBody = { token, newPassword }
    await apiClient.request<void>({
      method: 'POST',
      path: '/api/auth/password-reset/confirm',
      body,
      auth: false,
      skipRefresh: true,
    })
  },

  me(): Promise<UserDto> {
    return apiClient.request<UserDto>({ path: '/api/me' })
  },

  patchMe(body: PatchMeRequest): Promise<UserDto> {
    return apiClient.request<UserDto>({ method: 'PATCH', path: '/api/me', body })
  },

  /** Смена пароля: `currentPassword` обязателен — пароль есть всегда. */
  async setPassword(body: SetPasswordRequest): Promise<void> {
    await apiClient.request<void>({ method: 'POST', path: '/api/me/password', body })
  },
}

/* -------------------------------- Кампании -------------------------------- */

export const campaignsApi = {
  async list(): Promise<CampaignListItem[]> {
    const page = await apiClient.request<PageDto<CampaignListItem> | CampaignListItem[]>({
      path: '/api/campaigns',
    })
    return toPage(page).items
  },

  create(body: CreateCampaignRequest): Promise<CampaignDto> {
    return apiClient.request<CampaignDto>({ method: 'POST', path: '/api/campaigns', body })
  },

  get(campaignId: string): Promise<CampaignDto> {
    return apiClient.request<CampaignDto>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}`,
    })
  },

  patch(campaignId: string, body: PatchCampaignRequest): Promise<CampaignDto> {
    return apiClient.request<CampaignDto>({
      method: 'PATCH',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}`,
      body,
    })
  },

  async remove(campaignId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}`,
    })
  },
}

/* --------------------------- Участники и приглашения ---------------------- */

export const membersApi = {
  async list(campaignId: string): Promise<MemberDto[]> {
    const page = await apiClient.request<PageDto<MemberDto> | MemberDto[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/members`,
    })
    return toPage(page).items
  },

  add(campaignId: string, body: AddMemberRequest): Promise<AddMemberResponse> {
    return apiClient.request<AddMemberResponse>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/members`,
      body,
    })
  },

  patch(campaignId: string, memberId: string, body: PatchMemberRequest): Promise<MemberDto> {
    return apiClient.request<MemberDto>({
      method: 'PATCH',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/members/${encodeURIComponent(memberId)}`,
      body,
    })
  },

  transferOwnership(campaignId: string, memberId: string): Promise<MemberDto> {
    return apiClient.request<MemberDto>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/members/${encodeURIComponent(memberId)}/transfer-ownership`,
    })
  },

  async remove(campaignId: string, memberId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/members/${encodeURIComponent(memberId)}`,
    })
  },
}

export const invitesApi = {
  create(campaignId: string, body: CreateInviteRequest): Promise<InviteDto> {
    return apiClient.request<InviteDto>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/invites`,
      body,
    })
  },

  async list(campaignId: string): Promise<InviteDto[]> {
    const page = await apiClient.request<PageDto<InviteDto> | InviteDto[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/invites`,
    })
    return toPage(page).items
  },

  async revoke(campaignId: string, inviteId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/invites/${encodeURIComponent(inviteId)}`,
    })
  },

  /** Публичный предпросмотр — без авторизации (`api.md` §5). */
  preview(token: string): Promise<InvitePreviewDto> {
    return apiClient.request<InvitePreviewDto>({
      path: `/api/invites/${encodeURIComponent(token)}`,
      auth: false,
    })
  },

  /** Принятие приглашения: нужна авторизация, приглашение строго одноразовое. */
  accept(token: string): Promise<AcceptInviteResponse> {
    return apiClient.request<AcceptInviteResponse>({
      method: 'POST',
      path: `/api/invites/${encodeURIComponent(token)}/accept`,
    })
  },
}

/* --------------------------------- Объекты -------------------------------- */

/**
 * Четыре набора эндпоинтов вместо прежнего `/entities` (`api.md` §6):
 * `/heroes`, `/characters`, `/locations`, `/items`. Форма ответа — своя на класс.
 */
export const objectsApi = {
  async list<C extends ObjectClass>(
    campaignId: string,
    objectClass: C,
    query: ObjectListQuery = {},
  ): Promise<PageDto<ObjectDtoOf<C>>> {
    const page = await apiClient.request<PageDto<ObjectDtoOf<C>> | ObjectDtoOf<C>[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/${classPath(objectClass)}`,
      query: {
        q: query.q,
        updatedSince: query.updatedSince,
        limit: query.limit,
        offset: query.offset,
      },
    })
    return toPage(page)
  },

  get<C extends ObjectClass>(
    campaignId: string,
    objectClass: C,
    objectId: string,
  ): Promise<ObjectDtoOf<C>> {
    return apiClient.request<ObjectDtoOf<C>>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/${classPath(objectClass)}/${encodeURIComponent(objectId)}`,
    })
  },

  create<C extends ObjectClass>(
    campaignId: string,
    objectClass: C,
    body: CreateObjectMap[C],
  ): Promise<ObjectDtoOf<C>> {
    return apiClient.request<ObjectDtoOf<C>>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/${classPath(objectClass)}`,
      body,
    })
  },

  patch<C extends ObjectClass>(
    campaignId: string,
    objectClass: C,
    objectId: string,
    body: UpdateObjectMap[C],
    revision?: number,
  ): Promise<ObjectDtoOf<C>> {
    return apiClient.request<ObjectDtoOf<C>>({
      method: 'PATCH',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/${classPath(objectClass)}/${encodeURIComponent(objectId)}`,
      body,
      headers: revision === undefined ? undefined : { 'If-Match': String(revision) },
    })
  },

  async remove(campaignId: string, objectClass: ObjectClass, objectId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/${classPath(objectClass)}/${encodeURIComponent(objectId)}`,
    })
  },
}

/* -------------------------------- Пересказы ------------------------------- */

export const retellingsApi = {
  async list(campaignId: string, query: RetellingListQuery = {}): Promise<PageDto<RetellingDto>> {
    const page = await apiClient.request<PageDto<RetellingDto> | RetellingDto[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/retellings`,
      query: {
        q: query.q,
        authorEntityId: query.authorEntityId,
        limit: query.limit,
        offset: query.offset,
      },
    })
    return toPage(page)
  },

  get(campaignId: string, retellingId: string): Promise<RetellingDto> {
    return apiClient.request<RetellingDto>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/retellings/${encodeURIComponent(retellingId)}`,
    })
  },

  create(campaignId: string, body: CreateRetellingRequest): Promise<RetellingDto> {
    return apiClient.request<RetellingDto>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/retellings`,
      body,
    })
  },

  patch(campaignId: string, retellingId: string, body: PatchRetellingRequest): Promise<RetellingDto> {
    return apiClient.request<RetellingDto>({
      method: 'PATCH',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/retellings/${encodeURIComponent(retellingId)}`,
      body,
    })
  },

  async remove(campaignId: string, retellingId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/retellings/${encodeURIComponent(retellingId)}`,
    })
  },
}

/* ---------------------------------- Поиск --------------------------------- */

export const searchApi = {
  /** `kinds` — список видов через запятую (`api.md` §8); пусто — по всем классам. */
  search(campaignId: string, q: string, kinds?: readonly SearchKind[], limit = 50): Promise<SearchResponse> {
    const value = kinds && kinds.length > 0 ? kinds.join(',') : undefined
    return apiClient.request<SearchResponse>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/search`,
      query: { q, kinds: value, limit },
    })
  },
}

/* ------------------------------ Синхронизация ----------------------------- */

export const syncApi = {
  push(campaignId: string, body: SyncPushRequest): Promise<SyncPushResponse> {
    return apiClient.request<SyncPushResponse>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/sync/changes`,
      body,
      // Идентификатор установки: сервер не отдаёт клиенту его собственные правки
      // (`sync.md` §2) — дублируем его заголовком.
      headers: { 'X-Client-Id': body.clientId },
      timeoutMs: 60_000,
    })
  },

  /**
   * `GET .../sync/changes?since=&limit=` (`sync.md` §3.2). `clientId` передаётся,
   * чтобы сервер исключил изменения, сделанные этой же установкой.
   */
  pull(campaignId: string, since: number, limit = 500, clientId?: string): Promise<SyncPullResponse> {
    return apiClient.request<SyncPullResponse>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/sync/changes`,
      query: { since, limit, clientId },
      headers: clientId ? { 'X-Client-Id': clientId } : undefined,
      timeoutMs: 60_000,
    })
  },

  snapshot(campaignId: string, limit = 500, cursor?: string | null): Promise<SyncSnapshotResponse> {
    return apiClient.request<SyncSnapshotResponse>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/sync/snapshot`,
      query: { limit, cursor },
      timeoutMs: 60_000,
    })
  },

  state(campaignId: string): Promise<SyncStateDto> {
    return apiClient.request<SyncStateDto>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/sync/state`,
    })
  },
}

/* -------------------------------- Служебные ------------------------------- */

export const serviceApi = {
  health(): Promise<HealthDto> {
    return apiClient.request<HealthDto>({ path: '/api/health', auth: false })
  },

  version(): Promise<VersionDto> {
    return apiClient.request<VersionDto>({ path: '/api/version', auth: false })
  },
}

/** Обновление приложения для Android: версия и ссылка на apk. */
export const appApi = {
  latest(): Promise<AppLatestDto> {
    return apiClient.request<AppLatestDto>({ path: '/api/app/latest', auth: false })
  },

  versions(): Promise<AppVersionDto[]> {
    return apiClient.request<AppVersionDto[]>({ path: '/api/app/versions', auth: false })
  },
}

export interface RaceDto {
  code: string
  name: string
}

/** Справочник рас: подсказки в редакторе героя и персонажа. */
export const racesApi = {
  list(): Promise<RaceDto[]> {
    return apiClient.request<RaceDto[]>({ path: "/api/races" })
  },
}

/** Справочник классов персонажа: тот же формат, что у рас. */
export const classesApi = {
  list(): Promise<RaceDto[]> {
    return apiClient.request<RaceDto[]>({ path: "/api/classes" })
  },
}

/** Типы локаций: по коду выбирается картинка по умолчанию. */
export const locationTypesApi = {
  list(): Promise<RaceDto[]> {
    return apiClient.request<RaceDto[]>({ path: '/api/location-types' })
  },
}

/** Типы предметов. */
export const itemTypesApi = {
  list(): Promise<RaceDto[]> {
    return apiClient.request<RaceDto[]>({ path: '/api/item-types' })
  },
}

/** Фото-материалы кампании: карты, портреты, письма и документы. */
export const materialsApi = {
  list(campaignId: string, query = ""): Promise<{ items: MaterialDto[]; total: number }> {
    return apiClient.request({ path: `/api/campaigns/${campaignId}/materials`, query: { q: query || undefined } })
  },
  get(campaignId: string, materialId: string): Promise<MaterialDto> {
    return apiClient.request({ path: `/api/campaigns/${campaignId}/materials/${materialId}` })
  },
  create(campaignId: string, body: Partial<MaterialDto>): Promise<MaterialDto> {
    return apiClient.request({ path: `/api/campaigns/${campaignId}/materials`, method: "POST", body })
  },
  update(campaignId: string, materialId: string, body: Partial<MaterialDto>): Promise<MaterialDto> {
    return apiClient.request({
      path: `/api/campaigns/${campaignId}/materials/${materialId}`,
      method: "PATCH",
      body,
    })
  },
  remove(campaignId: string, materialId: string): Promise<void> {
    return apiClient.request({
      path: `/api/campaigns/${campaignId}/materials/${materialId}`,
      method: "DELETE",
    })
  },
}

export type { FileDto }

export const api = {
  auth: authApi,
  campaigns: campaignsApi,
  members: membersApi,
  invites: invitesApi,
  objects: objectsApi,
  retellings: retellingsApi,
  search: searchApi,
  sync: syncApi,
  service: serviceApi,
  app: appApi,
  races: racesApi,
  classes: classesApi,
  locationTypes: locationTypesApi,
  itemTypes: itemTypesApi,
  materials: materialsApi,
}
