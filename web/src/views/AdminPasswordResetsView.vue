<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { adminApi, errorMessage, type PasswordResetDto } from '@/api'

const items = ref<PasswordResetDto[]>([])
const email = ref('')
const link = ref<string | null>(null)
const error = ref<string | null>(null)
const busy = ref(false)

async function load(): Promise<void> {
  try {
    items.value = await adminApi.passwordResets()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function create(): Promise<void> {
  busy.value = true
  error.value = null
  link.value = null
  try {
    const result = await adminApi.createResetLink(email.value.trim())
    link.value = result.url
    email.value = ''
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Ссылки на сброс пароля</h1>
        <div class="page-header__subtitle">Сгенерируйте ссылку и отправьте её пользователю</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'settings' }">Настройки</RouterLink>
    </div>

    <form class="card stack" @submit.prevent="create">
      <label class="field__label" for="reset-email">Почта пользователя</label>
      <input id="reset-email" v-model="email" class="input" type="email" required />
      <button class="btn btn--primary" type="submit" :disabled="busy || !email.trim()">
        Сгенерировать ссылку
      </button>
      <p v-if="error" class="notice notice--danger">{{ error }}</p>
      <p v-if="link" class="notice notice--success" style="word-break: break-all">
        {{ link }}
      </p>
    </form>

    <div class="card stack">
      <h2>Ожидают сброса</h2>
      <p v-if="items.length === 0" class="faint">Нет активных ссылок.</p>
      <div v-for="item in items" :key="item.id" class="stack stack--sm">
        <strong>{{ item.login }} · {{ item.email }}</strong>
        <span class="faint">до {{ new Date(item.expiresAt).toLocaleString('ru') }}</span>
        <code style="word-break: break-all">{{ item.token }}</code>
      </div>
    </div>
  </div>
</template>
