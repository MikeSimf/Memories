<script setup lang="ts">
import { ref } from 'vue'

import { errorMessage } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const email = ref('')
const submitting = ref(false)
const sent = ref(false)
const localError = ref<string | null>(null)

/**
 * Сервер всегда отвечает `202` (`api.md` §3), поэтому по ответу нельзя узнать,
 * есть ли такой адрес: показываем нейтральный текст.
 */
async function submit(): Promise<void> {
  localError.value = null
  if (email.value.trim() === '') {
    localError.value = 'Укажите почту'
    return
  }
  submitting.value = true
  try {
    await auth.requestPasswordReset(email.value.trim())
    sent.value = true
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
        <h1 style="margin: 0">Сброс пароля</h1>
      </div>

      <template v-if="sent">
        <p class="notice notice--success">
          Если такой адрес есть, ссылка на сброс пароля отправлена администратору сервера.
        </p>
        <p class="faint">
          Почтовый сервер пока не подключён: администратор получит ссылку и передаст её вам.
          Ссылка действует один час.
        </p>
        <RouterLink class="btn btn--block" :to="{ name: 'login' }">Вернуться ко входу</RouterLink>
      </template>

      <template v-else>
        <p class="muted">
          Укажите почту аккаунта — мы передадим запрос администратору, и он пришлёт ссылку
          для нового пароля.
        </p>
        <form class="stack" @submit.prevent="submit">
          <div class="field">
            <label class="field__label" for="reset-email">Почта</label>
            <input id="reset-email" v-model="email" class="input" type="email" autocomplete="email" required />
          </div>

          <p v-if="localError" class="notice notice--danger">{{ localError }}</p>

          <button class="btn btn--primary btn--block" type="submit" :disabled="submitting">
            <span v-if="submitting" class="spinner" aria-hidden="true" />
            Отправить ссылку
          </button>
        </form>

        <p class="muted center" style="margin-top: 1rem">
          <RouterLink :to="{ name: 'login' }">Вернуться ко входу</RouterLink>
        </p>
      </template>
    </div>
  </div>
</template>
