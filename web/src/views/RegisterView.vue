<script setup lang="ts">
import { computed, ref } from 'vue'
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
const passwordAgain = ref('')
const displayName = ref('')
const submitting = ref(false)
const localError = ref<string | null>(null)

/** Приглашение из QR: приходит в `?invite=` и уходит в `POST /api/auth/register`. */
const inviteToken = computed(() => {
  const value = route.query.invite
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null
})

function redirectTarget(): string | { name: string } {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : { name: 'campaigns' }
}

async function submit(): Promise<void> {
  localError.value = null
  if (!/^[A-Za-z0-9_.-]{3,32}$/.test(login.value.trim())) {
    localError.value = 'Логин: 3–32 символа из латинских букв, цифр, «_», «.» и «-»'
    return
  }
  if (password.value.length < 8) {
    localError.value = 'Пароль должен быть не короче 8 символов'
    return
  }
  if (password.value !== passwordAgain.value) {
    localError.value = 'Пароли не совпадают'
    return
  }
  submitting.value = true
  try {
    await auth.register({
      login: login.value.trim(),
      password: password.value,
      displayName: displayName.value.trim(),
      ...(inviteToken.value ? { inviteToken: inviteToken.value } : {}),
    })
    ui.pushToast('success', inviteToken.value
      ? 'Аккаунт создан, вы в кампании'
      : 'Аккаунт создан')
    await router.replace(redirectTarget())
  } catch (caught) {
    localError.value = errorMessage(caught)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__title">
        <img src="/icons/icon-192.svg" alt="" width="40" height="40" />
        <h1 style="margin: 0">Регистрация</h1>
      </div>

      <p v-if="inviteToken" class="notice notice--accent">
        Вы регистрируетесь по приглашению в кампанию — после создания аккаунта сразу окажетесь в ней.
      </p>

      <form class="stack" @submit.prevent="submit">
        <div class="field">
          <label class="field__label" for="reg-login">Логин</label>
          <input
            id="reg-login"
            v-model="login"
            class="input"
            type="text"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            required
            minlength="3"
            maxlength="32"
          />
          <span class="field__hint">
            Для входа: 3–32 символа из латинских a–z, цифр, «_», «.» и «-». Регистр не важен.
          </span>
        </div>
        <div class="field">
          <label class="field__label" for="reg-name">Отображаемое имя</label>
          <input
            id="reg-name"
            v-model="displayName"
            class="input"
            type="text"
            autocomplete="nickname"
            required
          />
        </div>
        <div class="field">
          <label class="field__label" for="reg-password">Пароль</label>
          <input
            id="reg-password"
            v-model="password"
            class="input"
            type="password"
            autocomplete="new-password"
            required
            minlength="8"
          />
          <span class="field__hint">Не короче 8 символов.</span>
        </div>
        <div class="field">
          <label class="field__label" for="reg-password2">Пароль ещё раз</label>
          <input
            id="reg-password2"
            v-model="passwordAgain"
            class="input"
            type="password"
            autocomplete="new-password"
            required
          />
        </div>

        <p v-if="localError" class="notice notice--danger">{{ localError }}</p>

        <button class="btn btn--primary btn--block" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true" />
          Создать аккаунт
        </button>
      </form>

      <p class="muted center" style="margin-top: 1rem">
        Уже есть аккаунт?
        <RouterLink :to="{ name: 'login', query: route.query }">Войти</RouterLink>
      </p>
    </div>
  </div>
</template>
