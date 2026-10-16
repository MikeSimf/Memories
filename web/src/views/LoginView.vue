<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const login = ref('')
const password = ref('')
const inviteCode = ref('')
const submitting = ref(false)
const localError = ref<string | null>(null)
const showInvite = ref(false)

function redirectTarget(): string | { name: string } {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : { name: 'campaigns' }
}

async function submit(): Promise<void> {
  localError.value = null
  if (login.value.trim() === '' || password.value === '') {
    localError.value = 'Введите логин и пароль'
    return
  }
  submitting.value = true
  try {
    await auth.login(login.value.trim(), password.value)
    ui.pushToast('success', 'Добро пожаловать!')
    await router.replace(redirectTarget())
  } catch (caught) {
    localError.value = errorMessage(caught)
  } finally {
    submitting.value = false
  }
}

/** «У меня есть приглашение»: код ведёт на страницу приглашения `/join/<token>`. */
async function openInvite(): Promise<void> {
  const token = inviteCode.value.trim().toUpperCase()
  if (token === '') {
    localError.value = 'Введите код приглашения'
    return
  }
  await router.push({ name: 'join', params: { token } })
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__title">
        <img src="/icons/icon-192.svg" alt="" width="40" height="40" />
        <div>
          <h1 style="margin: 0">DnD Memory</h1>
          <span class="faint">Справочник настольной сессии</span>
        </div>
      </div>

      <form class="stack" @submit.prevent="submit">
        <div class="field">
          <label class="field__label" for="login-name">Логин</label>
          <input
            id="login-name"
            v-model="login"
            class="input"
            type="text"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            required
            placeholder="gm"
          />
        </div>
        <div class="field">
          <label class="field__label" for="login-password">Пароль</label>
          <input
            id="login-password"
            v-model="password"
            class="input"
            type="password"
            autocomplete="current-password"
            required
          />
        </div>

        <p v-if="localError" class="notice notice--danger">{{ localError }}</p>

        <button class="btn btn--primary btn--block" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true" />
          Войти
        </button>
      </form>

      <p class="muted center" style="margin-top: 0.75rem">
        <RouterLink :to="{ name: 'password-reset-request' }">Забыли пароль?</RouterLink>
      </p>

      <div class="divider">или</div>

      <template v-if="showInvite">
        <form class="stack stack--sm" @submit.prevent="openInvite">
          <div class="field">
            <label class="field__label" for="login-invite">Код приглашения</label>
            <input
              id="login-invite"
              v-model="inviteCode"
              class="input token-code"
              type="text"
              autocapitalize="characters"
              spellcheck="false"
              maxlength="8"
              placeholder="7F3K9Q2M"
            />
            <span class="field__hint">Восемь символов из QR-кода приглашения.</span>
          </div>
          <button class="btn btn--block" type="submit">Открыть приглашение</button>
        </form>
      </template>
      <button v-else class="btn btn--block" type="button" @click="showInvite = true">
        У меня есть приглашение
      </button>

      <p class="muted center" style="margin-top: 1rem">
        Нет аккаунта?
        <RouterLink :to="{ name: 'register', query: route.query }">Зарегистрироваться</RouterLink>
      </p>
    </div>
  </div>
</template>
