/**
 * Хранилище токенов. Access-токен живёт 15 минут, refresh — 30 дней с ротацией
 * (`api.md` §1), поэтому оба лежат в localStorage и переживают перезапуск вкладки.
 */

import { createUuid } from '../utils/uuid'

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  /** Момент истечения access-токена, мс epoch. */
  expiresAt: number
}

export interface UserProfile {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
}

const TOKENS_KEY = 'dnd-memory.tokens'
const USER_KEY = 'dnd-memory.user'
/** Запас до истечения, чтобы не отправить заведомо просроченный токен. */
const EXPIRY_SKEW_MS = 15_000

function safeStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export const tokenStore = {
  get(): AuthTokens | null {
    const storage = safeStorage()
    if (!storage) return null
    const raw = storage.getItem(TOKENS_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as Partial<AuthTokens>
      if (typeof parsed.accessToken !== 'string' || typeof parsed.refreshToken !== 'string') {
        return null
      }
      return {
        accessToken: parsed.accessToken,
        refreshToken: parsed.refreshToken,
        expiresAt: typeof parsed.expiresAt === 'number' ? parsed.expiresAt : 0,
      }
    } catch {
      return null
    }
  },

  set(tokens: AuthTokens): void {
    safeStorage()?.setItem(TOKENS_KEY, JSON.stringify(tokens))
  },

  clear(): void {
    const storage = safeStorage()
    storage?.removeItem(TOKENS_KEY)
    storage?.removeItem(USER_KEY)
  },

  get accessToken(): string | null {
    return this.get()?.accessToken ?? null
  },

  get refreshToken(): string | null {
    return this.get()?.refreshToken ?? null
  },

  /** Токен есть и он ещё не истёк. */
  isFresh(): boolean {
    const tokens = this.get()
    if (!tokens) return false
    return tokens.expiresAt - EXPIRY_SKEW_MS > Date.now()
  },
}

export const userStore = {
  get(): UserProfile | null {
    const storage = safeStorage()
    if (!storage) return null
    const raw = storage.getItem(USER_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as Partial<UserProfile>
      if (typeof parsed.id !== 'string') return null
      return {
        id: parsed.id,
        email: typeof parsed.email === 'string' ? parsed.email : '',
        displayName: typeof parsed.displayName === 'string' ? parsed.displayName : '',
        avatarUrl: typeof parsed.avatarUrl === 'string' ? parsed.avatarUrl : null,
      }
    } catch {
      return null
    }
  },

  set(user: UserProfile): void {
    safeStorage()?.setItem(USER_KEY, JSON.stringify(user))
  },

  clear(): void {
    safeStorage()?.removeItem(USER_KEY)
  },
}

/** Идентификатор установки приложения — `clientId` в теле push (`sync.md` §2). */
const DEVICE_KEY = 'dnd-memory.device-id'

export function getDeviceId(): string {
  const storage = safeStorage()
  if (!storage) return 'device-unknown'
  let id = storage.getItem(DEVICE_KEY)
  if (!id) {
    id = `device-${createUuid()}`
    storage.setItem(DEVICE_KEY, id)
  }
  return id
}
