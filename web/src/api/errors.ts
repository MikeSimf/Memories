import { API_ERROR_MESSAGES, type ApiErrorBody, type ApiErrorCode } from './types'

/**
 * Ошибка API: код из контракта, HTTP-статус, детали и `X-Request-Id`
 * (последний нужен, чтобы показать его пользователю при 5xx — `api.md` §12).
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: ApiErrorCode
  readonly details: Record<string, unknown>
  readonly requestId: string | null

  constructor(init: {
    status: number
    code: ApiErrorCode
    message: string
    details?: Record<string, unknown>
    requestId?: string | null
  }) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.details = init.details ?? {}
    this.requestId = init.requestId ?? null
  }

  /** Текст для пользователя: сообщение сервера + идентификатор запроса при 5xx. */
  get userMessage(): string {
    const base = this.message || API_ERROR_MESSAGES[this.code] || API_ERROR_MESSAGES.unknown
    if (this.requestId && this.status >= 500) {
      return `${base} (код запроса: ${this.requestId})`
    }
    return base
  }

  get isOffline(): boolean {
    return this.code === 'network_error'
  }

  get isUnauthorized(): boolean {
    return this.status === 401 || this.code === 'unauthorized'
  }

  get isForbidden(): boolean {
    return this.status === 403 || this.code === 'forbidden'
  }

  get isNotFound(): boolean {
    return this.status === 404 || this.code === 'not_found'
  }

  get isConflict(): boolean {
    return this.status === 409 || this.code === 'conflict'
  }

  /** Тело ошибки в формате контракта — удобно для повторной обработки. */
  toBody(): ApiErrorBody {
    return { error: { code: this.code, message: this.message, details: this.details } }
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

/** Сообщение об ошибке для показа пользователю: без «[object Object]» и без стектрейсов. */
export function errorMessage(value: unknown): string {
  if (isApiError(value)) {
    return value.userMessage
  }
  if (value instanceof Error) {
    return value.message || API_ERROR_MESSAGES.unknown
  }
  if (typeof value === 'string' && value.trim() !== '') {
    return value
  }
  return API_ERROR_MESSAGES.unknown
}

export function toApiError(value: unknown): ApiError {
  if (isApiError(value)) {
    return value
  }
  return new ApiError({
    status: 0,
    code: 'unknown',
    message: errorMessage(value),
  })
}
