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
  type PatchMeRequest,
  type RegisterRequest,
  type SetPasswordRequest,
  type UserDto,
  type UserProfile,
} from '@/api'
import { clearDatabase } from '@/db'
import { useUiStore } from './ui'

/** Вход — только по логину и паролю: провайдеров (Яндекс, VK) больше нет. */
export const useAuthStore = defineStore('auth', () => {
  const ui = useUiStore()

  const user = ref<UserProfile | null>(userStore.get())
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(
    () => user.value !== null && tokenStore.get()?.accessToken !== undefined,
  )

  /** `UserDto` → локальный профиль: логин, почта и признак пароля нужны настройкам. */
  function toProfile(dto: UserDto): UserProfile {
    return {
      id: dto.id,
      login: dto.login ?? '',
      email: dto.email ?? '',
      emailVerified: dto.emailVerified === true,
      displayName: dto.displayName,
      avatarUrl: dto.avatarUrl ?? null,
      phone: dto.phone ?? null,
      hasPassword: dto.hasPassword === true,
    }
  }

  function applyAuth(response: AuthResponse): void {
    tokenStore.set({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
    })
    const profile = toProfile(response.user)
    userStore.set(profile)
    user.value = profile
  }

  async function login(login: string, password: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      applyAuth(await authApi.login({ login, password }))
    } catch (caught) {
      error.value = errorMessage(caught)
      throw caught
    } finally {
      loading.value = false
    }
  }

  /**
   * Регистрация: логин, почта, пароль, имя. `inviteToken` передаётся при переходе
   * по QR — пользователь создаётся и сразу присоединяется к кампании (`api.md` §3).
   */
  async function register(body: RegisterRequest): Promise<void> {
    loading.value = true
    error.value = null
    try {
      applyAuth(await authApi.register(body))
    } catch (caught) {
      error.value = errorMessage(caught)
      throw caught
    } finally {
      loading.value = false
    }
  }

  /**
   * «Забыли пароль?»: сервер всегда отвечает `202`, поэтому интерфейс показывает
   * нейтральный текст — по ответу нельзя узнать, есть ли такой адрес (`api.md` §3).
   */
  async function requestPasswordReset(email: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await authApi.requestPasswordReset(email)
    } catch (caught) {
      error.value = errorMessage(caught)
      throw caught
    } finally {
      loading.value = false
    }
  }

  /** Новый пароль по токену из ссылки `#/reset/:token`; все сессии гасятся. */
  async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await authApi.confirmPasswordReset(token, newPassword)
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

  async function refreshProfile(): Promise<void> {
    if (!tokenStore.get()) {
      user.value = null
      return
    }
    try {
      const me = await authApi.me()
      const profile = toProfile(me)
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

  /** `PATCH /api/me`: имя, почта, телефон. Ошибки не глотаем — их показывает экран. */
  async function updateProfile(body: PatchMeRequest): Promise<void> {
    const me = await authApi.patchMe(body)
    const profile = toProfile(me)
    userStore.set(profile)
    user.value = profile
  }

  /** `POST /api/me/password`: текущий пароль обязателен — пароль есть всегда. */
  async function setPassword(body: SetPasswordRequest): Promise<void> {
    await authApi.setPassword(body)
    await refreshProfile()
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
    isAuthenticated,
    initialize,
    login,
    register,
    requestPasswordReset,
    confirmPasswordReset,
    logout,
    forceLogout,
    refreshProfile,
    updateProfile,
    setPassword,
  }
})
