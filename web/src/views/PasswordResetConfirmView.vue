<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError, errorMessage } from '@/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const token = computed(() => String(route.params.token ?? ''))
const password = ref('')
const passwordAgain = ref('')
const submitting = ref(false)
const done = ref(false)
const localError = ref<string | null>(null)

/** `POST /api/auth/password-reset/confirm` (`api.md` §3): все сессии гасятся. */
async function submit(): Promise<void> {
  localError.value = null
  if (token.value === '') {
    localError.value = 'В ссылке нет токена сброса — запросите новую ссылку'
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
    await auth.confirmPasswordReset(token.value, password.value)
    done.value = true
  } catch (caught) {
    if (caught instanceof ApiError && caught.code === 'reset_invalid') {
      localError.value = 'Ссылка недействительна или просрочена, запросите новую'
    } else {
      localError.value = errorMessage(caught)
    }
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
        <h1 style="margin: 0">Новый пароль</h1>
      </div>

      <template v-if="done">
        <p class="notice notice--success">Пароль изменён. Войдите с новым паролем.</p>
        <RouterLink class="btn btn--primary btn--block" :to="{ name: 'login' }">Войти</RouterLink>
      </template>

      <form v-else class="stack" @submit.prevent="submit">
        <div class="field">
          <label class="field__label" for="reset-password">Новый пароль</label>
          <input
            id="reset-password"
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
          <label class="field__label" for="reset-password2">Пароль ещё раз</label>
          <input
            id="reset-password2"
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
          Сохранить пароль
        </button>
      </form>

      <p class="muted center" style="margin-top: 1rem">
        <RouterLink :to="{ name: 'password-reset-request' }">Запросить новую ссылку</RouterLink>
      </p>
    </div>
  </div>
</template>
