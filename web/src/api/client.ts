import { ApiError, isApiError } from './errors'
import { tokenStore, type AuthTokens } from './tokens'
import type { ApiErrorBody, ApiErrorCode, RefreshResponse } from './types'

export type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

export type QueryValue = string | number | boolean | null | undefined

export interface RequestOptions {
  method?: HttpMethod
  /** Путь, начинающийся с `/api`. */
  path: string
  query?: Record<string, QueryValue>
  body?: unknown
  /** Требуется авторизация (по умолчанию — да). */
  auth?: boolean
  headers?: Record<string, string>
  signal?: AbortSignal
  timeoutMs?: number
  /** Не пытаться обновить токен (используется самим `/auth/refresh`). */
  skipRefresh?: boolean
}

export interface RawResponse {
  status: number
  ok: boolean
  headers: Headers
  text: string
  contentType: string
}

const DEFAULT_TIMEOUT_MS = 20_000

/**
 * Базовый адрес API. Пустое значение (по умолчанию) означает относительные
 * запросы `/api/...` — так работает раздача статики через nginx в docker-compose.
 */
export function apiBaseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL ?? ''
  return raw.replace(/\/+$/, '')
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${apiBaseUrl()}${path}`
  if (!query) return url
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    params.append(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${url}?${qs}` : url
}

function parseErrorCode(value: unknown): ApiErrorCode {
  return typeof value === 'string' ? (value as ApiErrorCode) : 'unknown'
}

function isErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false
  const candidate = (value as { error?: unknown }).error
  return typeof candidate === 'object' && candidate !== null
}

/**
 * HTTP-клиент: JSON, Bearer-токен, автообновление по 401 с повтором запроса
 * (`api.md` §1, §3) и единый формат ошибки.
 */
export class ApiClient {
  private refreshPromise: Promise<AuthTokens | null> | null = null
  private onUnauthorized: (() => void) | null = null

  /** Вызывается, когда обновить токен не удалось — UI уводит на вход. */
  setUnauthorizedHandler(handler: (() => void) | null): void {
    this.onUnauthorized = handler
  }

  async request<T>(options: RequestOptions): Promise<T> {
    const raw = await this.raw(options)
    if (raw.status === 204 || raw.text === '') {
      return undefined as T
    }
    return JSON.parse(raw.text) as T
  }

  async raw(options: RequestOptions): Promise<RawResponse> {
    const auth = options.auth !== false
    let response = await this.send(options, auth)

    if (response.status === 401 && auth && !options.skipRefresh) {
      const tokens = await this.refreshTokens()
      if (!tokens) {
        this.onUnauthorized?.()
        throw await this.toError(response)
      }
      response = await this.send(options, auth)
      if (response.status === 401) {
        tokenStore.clear()
        this.onUnauthorized?.()
        throw await this.toError(response)
      }
    }

    if (!response.ok) {
      throw await this.toError(response)
    }
    return response
  }

  private async send(options: RequestOptions, auth: boolean): Promise<RawResponse> {
    const response = await this.fetchWithAuth(options, auth)
    return this.readRaw(response)
  }

  private async readRaw(response: Response): Promise<RawResponse> {
    const text = await response.text()
    return {
      status: response.status,
      ok: response.ok,
      headers: response.headers,
      text,
      contentType: response.headers.get('Content-Type') ?? '',
    }
  }

  private async fetchWithAuth(options: RequestOptions, auth: boolean): Promise<Response> {
    const controller = new AbortController()
    const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    const onExternalAbort = (): void => controller.abort()
    options.signal?.addEventListener('abort', onExternalAbort)

    const headers = new Headers(options.headers ?? {})
    headers.set('Accept', 'application/json')
    if (options.body !== undefined) {
      headers.set('Content-Type', 'application/json')
    }
    if (auth) {
      const token = tokenStore.accessToken
      if (token) headers.set('Authorization', `Bearer ${token}`)
    }

    try {
      return await fetch(buildUrl(options.path, options.query), {
        method: options.method ?? 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal,
        credentials: 'omit',
        cache: 'no-store',
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        if (options.signal?.aborted) {
          throw new ApiError({ status: 0, code: 'network_error', message: 'Запрос отменён' })
        }
        throw new ApiError({
          status: 0,
          code: 'network_error',
          message: 'Сервер не ответил вовремя',
        })
      }
      throw new ApiError({
        status: 0,
        code: 'network_error',
        message: 'Нет связи с сервером',
      })
    } finally {
      clearTimeout(timer)
      options.signal?.removeEventListener('abort', onExternalAbort)
    }
  }

  /**
   * Двоичный ответ с авторизацией — используется для изображений объектов
   * (`GET /api/files/{id}` требует токен, `api.md` §10).
   */
  async fetchBlob(path: string, options: { auth?: boolean; signal?: AbortSignal } = {}): Promise<Blob> {
    const auth = options.auth !== false
    let response = await this.fetchWithAuth({ path, signal: options.signal }, auth)
    if (response.status === 401 && auth) {
      const tokens = await this.refreshTokens()
      if (!tokens) {
        this.onUnauthorized?.()
        throw await this.toError(await this.readRaw(response))
      }
      response = await this.fetchWithAuth({ path, signal: options.signal }, auth)
    }
    if (!response.ok) {
      throw await this.toError(await this.readRaw(response))
    }
    return response.blob()
  }

  private async toError(response: RawResponse): Promise<ApiError> {
    const requestId = response.headers.get('X-Request-Id')
    let code: ApiErrorCode = 'unknown'
    let message = ''
    let details: Record<string, unknown> = {}
    if (response.text !== '') {
      try {
        const parsed: unknown = JSON.parse(response.text)
        if (isErrorBody(parsed)) {
          code = parseErrorCode(parsed.error.code)
          message = typeof parsed.error.message === 'string' ? parsed.error.message : ''
          details =
            typeof parsed.error.details === 'object' && parsed.error.details !== null
              ? parsed.error.details
              : {}
        }
      } catch {
        message = response.text.slice(0, 300)
      }
    }
    if (!message) {
      message = `HTTP ${response.status}`
    }
    if (response.status === 403) code = 'forbidden'
    if (response.status === 401) code = 'unauthorized'
    if (response.status === 404) code = 'not_found'
    if (response.status === 409 && code === 'unknown') code = 'conflict'
    return new ApiError({ status: response.status, code, message, details, requestId })
  }

  /**
   * Обновление пары токенов. Запросы объединяются: параллельные 401 не порождают
   * несколько обращений к `/auth/refresh` (иначе ротация токена сломает сессию).
   */
  async refreshTokens(): Promise<AuthTokens | null> {
    if (this.refreshPromise) return this.refreshPromise
    const current = tokenStore.get()
    if (!current?.refreshToken) return null
    this.refreshPromise = this.performRefresh(current.refreshToken).finally(() => {
      this.refreshPromise = null
    })
    return this.refreshPromise
  }

  private async performRefresh(refreshToken: string): Promise<AuthTokens | null> {
    try {
      const response = await this.request<RefreshResponse>({
        method: 'POST',
        path: '/api/auth/refresh',
        body: { refreshToken },
        auth: false,
        skipRefresh: true,
      })
      const tokens: AuthTokens = {
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        expiresAt: Date.now() + response.expiresIn * 1000,
      }
      tokenStore.set(tokens)
      return tokens
    } catch (error) {
      if (isApiError(error) && error.isOffline) {
        // Сеть пропала — токены сохраняем, попробуем позже.
        return null
      }
      tokenStore.clear()
      return null
    }
  }
}

export const apiClient = new ApiClient()

/** Скачивание файла (например, картинки объекта) с авторизацией. */
export async function fetchFileBlob(fileId: string): Promise<Blob> {
  return apiClient.fetchBlob(`/api/files/${encodeURIComponent(fileId)}`)
}

/** Абсолютный адрес файла — используется как значение по умолчанию (без токена). */
export function fileUrl(fileId: string): string {
  return buildUrl(`/api/files/${encodeURIComponent(fileId)}`)
}

/** Загрузка файла: `multipart/form-data`, поле `file` (`api.md` §10). */
export async function uploadFile(file: File): Promise<import('./types').FileDto> {
  const form = new FormData()
  form.append('file', file)
  const token = tokenStore.accessToken
  const response = await fetch(buildUrl('/api/files'), {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    body: form,
    credentials: 'omit',
  })
  if (!response.ok) {
    throw new ApiError({
      status: response.status,
      code: response.status === 401 ? 'unauthorized' : 'unknown',
      message: `Не удалось загрузить файл (HTTP ${response.status})`,
      requestId: response.headers.get('X-Request-Id'),
    })
  }
  return (await response.json()) as import('./types').FileDto
}
