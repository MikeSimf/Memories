import { apiClient } from './client'
import type {
  AcceptInviteResponse,
  AddMemberRequest,
  AddMemberResponse,
  AuthResponse,
  BacklinkDto,
  CampaignDto,
  CampaignListItem,
  CreateCampaignRequest,
  CreateEntityRequest,
  CreateEnrollmentRequest,
  CreateInviteRequest,
  CreateRetellingRequest,
  DeviceDto,
  EntityDto,
  EntityListQuery,
  EnrollRequest,
  EnrollResponse,
  FileDto,
  HealthDto,
  InviteDto,
  InvitePreviewDto,
  LoginRequest,
  LogoutRequest,
  MemberDto,
  OAuthCallbackRequest,
  OAuthProvider,
  OAuthProvidersResponse,
  PageDto,
  PatchCampaignRequest,
  PatchEntityRequest,
  PatchMeRequest,
  PatchMemberRequest,
  PatchRetellingRequest,
  RefreshRequest,
  RefreshResponse,
  RegisterRequest,
  RetellingDto,
  RetellingListQuery,
  SearchResponse,
  SetPasswordRequest,
  SyncPullResponse,
  SyncPushRequest,
  SyncPushResponse,
  SyncSnapshotResponse,
  SyncStateDto,
  UserDto,
  VersionDto,
} from './types'

/** Список приходит либо страницей, либо «сырым» массивом — приводим к странице. */
function toPage<T>(value: PageDto<T> | T[] | null): PageDto<T> {
  if (Array.isArray(value)) {
    return { items: value, total: value.length }
  }
  if (!value || !Array.isArray(value.items)) {
    return { items: [], total: 0 }
  }
  return value
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

  me(): Promise<UserDto> {
    return apiClient.request<UserDto>({ path: '/api/me' })
  },

  patchMe(body: PatchMeRequest): Promise<UserDto> {
    return apiClient.request<UserDto>({ method: 'PATCH', path: '/api/me', body })
  },

  /**
   * Задать или сменить пароль (`api.md` §3). `currentPassword` обязателен только
   * при `hasPassword: true`. После запроса аккаунт устройства становится полноценным.
   */
  async setPassword(body: SetPasswordRequest): Promise<void> {
    await apiClient.request<void>({ method: 'POST', path: '/api/me/password', body })
  },

  providers(): Promise<OAuthProvidersResponse> {
    return apiClient.request<OAuthProvidersResponse>({ path: '/api/auth/providers', auth: false })
  },

  /**
   * Адрес начала OAuth-входа (`api.md` §3): переход по нему отдаёт 302 на страницу
   * провайдера, а тот возвращает код на `redirectUri` — для веба это `/oauth/callback`.
   */
  oauthAuthorizeUrl(provider: OAuthProvider, redirectUri: string, state: string): string {
    const base = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')
    const params = new URLSearchParams({ redirectUri, state })
    return `${base}/api/auth/oauth/${provider}/authorize?${params.toString()}`
  },

  oauthCallback(provider: OAuthProvider, body: OAuthCallbackRequest): Promise<AuthResponse> {
    return apiClient.request<AuthResponse>({
      method: 'POST',
      path: `/api/auth/oauth/${provider}/callback`,
      body,
      auth: false,
    })
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

  accept(token: string): Promise<AcceptInviteResponse> {
    return apiClient.request<AcceptInviteResponse>({
      method: 'POST',
      path: `/api/invites/${encodeURIComponent(token)}/accept`,
    })
  },
}

/* --------------------- Регистрация устройства по QR ----------------------- */

export const devicesApi = {
  /** Шаг админа: выпуск QR-кода для устройства (`api.md` §3.1, право `members.manage`). */
  createEnrollment(campaignId: string, body: CreateEnrollmentRequest): Promise<InviteDto> {
    return apiClient.request<InviteDto>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/devices/enrollments`,
      body,
    })
  },

  /** Список устройств кампании (`members.manage`). */
  async list(campaignId: string): Promise<DeviceDto[]> {
    const page = await apiClient.request<PageDto<DeviceDto> | DeviceDto[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/devices`,
    })
    return toPage(page).items
  },

  /** Отзыв устройства: его сессии гасятся, человек остаётся в кампании. */
  async revoke(campaignId: string, deviceId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/devices/${encodeURIComponent(deviceId)}`,
    })
  },

  /** Шаг телефона: единственный эндпоинт без авторизации (кроме входа и `/health`). */
  enroll(body: EnrollRequest): Promise<EnrollResponse> {
    return apiClient.request<EnrollResponse>({
      method: 'POST',
      path: '/api/devices/enroll',
      body,
      auth: false,
    })
  },
}

/* --------------------------------- Объекты -------------------------------- */

export const entitiesApi = {
  async list(campaignId: string, query: EntityListQuery = {}): Promise<PageDto<EntityDto>> {
    // `types` — список видов через запятую: `?types=HERO,NPC` (`api.md` §6).
    const types = query.types && query.types.length > 0 ? query.types.join(',') : undefined
    const page = await apiClient.request<PageDto<EntityDto> | EntityDto[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/entities`,
      query: {
        types,
        q: query.q,
        updatedSince: query.updatedSince,
        limit: query.limit,
        offset: query.offset,
      },
    })
    return toPage(page)
  },

  get(campaignId: string, entityId: string): Promise<EntityDto> {
    return apiClient.request<EntityDto>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/entities/${encodeURIComponent(entityId)}`,
    })
  },

  create(campaignId: string, body: CreateEntityRequest): Promise<EntityDto> {
    return apiClient.request<EntityDto>({
      method: 'POST',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/entities`,
      body,
    })
  },

  patch(campaignId: string, entityId: string, body: PatchEntityRequest, revision?: number): Promise<EntityDto> {
    return apiClient.request<EntityDto>({
      method: 'PATCH',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/entities/${encodeURIComponent(entityId)}`,
      body,
      headers: revision === undefined ? undefined : { 'If-Match': String(revision) },
    })
  },

  async remove(campaignId: string, entityId: string): Promise<void> {
    await apiClient.request<void>({
      method: 'DELETE',
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/entities/${encodeURIComponent(entityId)}`,
    })
  },

  async backlinks(campaignId: string, entityId: string): Promise<BacklinkDto[]> {
    const page = await apiClient.request<PageDto<BacklinkDto> | BacklinkDto[]>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/entities/${encodeURIComponent(entityId)}/backlinks`,
    })
    return toPage(page).items
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
  search(campaignId: string, q: string, types?: string, limit = 50): Promise<SearchResponse> {
    return apiClient.request<SearchResponse>({
      path: `/api/campaigns/${encodeURIComponent(campaignId)}/search`,
      query: { q, types, limit },
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

export type { FileDto }

export const api = {
  auth: authApi,
  campaigns: campaignsApi,
  members: membersApi,
  invites: invitesApi,
  devices: devicesApi,
  entities: entitiesApi,
  retellings: retellingsApi,
  search: searchApi,
  sync: syncApi,
  service: serviceApi,
}
