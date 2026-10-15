import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  ApiError,
  apiClient,
  authApi,
  errorMessage,
  tokenStore,
  userStore,
  type AuthResponse,
  type OAuthProvider,
  type OAuthProvidersResponse,
  type UserProfile,
} from '@/api'
import { clearDatabase } from '@/db'
import { useUiStore } from './ui'

const OAUTH_STATE_KEY = 'dnd-memory.oauth-state'
const OAUTH_PROVIDER_KEY = 'dnd-memory.oauth-provider'

export const useAuthStore = defineStore('auth', () => {
  const ui = useUiStore()

  const user = ref<UserProfile | null>(userStore.get())
  const loading = ref(false)
  const error = ref<string | null>(null)
  const providers = ref<OAuthProvidersResponse | null>(null)

  const isAuthenticated = computed(
    () => user.value !== null && tokenStore.get()?.accessToken !== undefined,
  )

  function applyAuth(response: AuthResponse): void {
    tokenStore.set({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
    })
    const profile: UserProfile = {
      id: response.user.id,
      email: response.user.email,
      displayName: response.user.displayName,
      avatarUrl: response.user.avatarUrl,
    }
    userStore.set(profile)
    user.value = profile
  }

  async function login(email: string, password: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      applyAuth(await authApi.login({ email, password }))
    } catch (caught) {
      error.value = errorMessage(caught)
      throw caught
    } finally {
      loading.value = false
    }
  }

  async function register(email: string, password: string, displayName: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      applyAuth(await authApi.register({ email, password, displayName }))
    } catch (caught) {
      error.value = errorMessage(caught)
      throw caught
    } finally {
      loading.value = false
    }
  }

  /** Выход: отзываем refresh-токен, чистим локальную базу (данные чужого аккаунта). */
  async function logout(): Promise<void> {
    const refreshToken = tokenStore.refreshToken
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken)
      } catch (caught) {
        if (!(caught instanceof ApiError) || !caught.isOffline) {
          console.warn('[dnd-memory] не удалось отозвать refresh-токен', caught)
        }
      }
    }
    await clearLocalState()
  }

  async function clearLocalState(): Promise<void> {
    tokenStore.clear()
    userStore.clear()
    user.value = null
    try {
      await clearDatabase()
    } catch (caught) {
      console.warn('[dnd-memory] не удалось очистить локальную базу', caught)
    }
  }

  function forceLogout(): void {
    tokenStore.clear()
    user.value = null
  }

  /** Адрес страницы возврата для OAuth — маршрут `/oauth/callback`. */
  function oauthRedirectUri(): string {
    return `${window.location.origin}/oauth/callback`
  }

  /** Начало входа через провайдера: запоминаем `state` и уходим на его страницу. */
  async function startOAuth(provider: OAuthProvider): Promise<string> {
    const state = crypto.randomUUID()
    try {
      window.sessionStorage.setItem(OAUTH_STATE_KEY, state)
      window.sessionStorage.setItem(OAUTH_PROVIDER_KEY, provider)
    } catch {
      /* sessionStorage может быть недоступен — state проверим мягко */
    }
    return authApi.oauthAuthorizeUrl(provider, oauthRedirectUri(), state)
  }

  async function completeOAuth(
    provider: OAuthProvider,
    params: { code: string | null; state: string | null; error?: string | null },
  ): Promise<void> {
    loading.value = true
    error.value = null
    try {
      if (params.error) {
        throw new ApiError({ status: 400, code: 'unauthorized', message: params.error })
      }
      if (!params.code) {
        throw new ApiError({
          status: 400,
          code: 'validation_failed',
          message: 'Провайдер не вернул код авторизации',
        })
      }
      const expectedState = window.sessionStorage.getItem(OAUTH_STATE_KEY)
      const storedProvider = window.sessionStorage.getItem(OAUTH_PROVIDER_KEY) as OAuthProvider | null
      if (expectedState && params.state && expectedState !== params.state) {
        throw new ApiError({
          status: 400,
          code: 'unauthorized',
          message: 'Проверка state не пройдена, попробуйте войти заново',
        })
      }
      applyAuth(
        await authApi.oauthCallback(provider ?? storedProvider ?? 'yandex', {
          code: params.code,
          redirectUri: oauthRedirectUri(),
          state: params.state ?? '',
        }),
      )
      window.sessionStorage.removeItem(OAUTH_STATE_KEY)
      window.sessionStorage.removeItem(OAUTH_PROVIDER_KEY)
    } catch (caught) {
      error.value = errorMessage(caught)
      throw caught
    } finally {
      loading.value = false
    }
  }

  async function loadProviders(): Promise<void> {
    try {
      providers.value = await authApi.providers()
    } catch (caught) {
      // Без списка провайдеров экран входа всё равно работает по почте и паролю.
      console.warn('[dnd-memory] не удалось получить список OAuth-провайдеров', caught)
      providers.value = null
    }
  }

  async function refreshProfile(): Promise<void> {
    if (!tokenStore.get()) {
      user.value = null
      return
    }
    try {
      const me = await authApi.me()
      const profile: UserProfile = {
        id: me.id,
        email: me.email,
        displayName: me.displayName,
        avatarUrl: me.avatarUrl,
      }
      userStore.set(profile)
      user.value = profile
    } catch (caught) {
      if (caught instanceof ApiError && caught.isUnauthorized) {
        forceLogout()
        return
      }
      // Нет сети — используем сохранённый профиль.
      ui.pushToast('warning', 'Профиль загружен из локального хранилища')
    }
  }

  function initialize(): void {
    user.value = userStore.get()
    apiClient.setUnauthorizedHandler(() => {
      forceLogout()
    })
  }

  return {
    user,
    loading,
    error,
    providers,
    isAuthenticated,
    initialize,
    login,
    register,
    logout,
    forceLogout,
    startOAuth,
    completeOAuth,
    loadProviders,
    refreshProfile,
  }
})
