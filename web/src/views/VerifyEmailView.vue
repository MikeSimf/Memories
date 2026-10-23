<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { apiClient, errorMessage } from '@/api'

/** Подтверждение почты по ссылке из письма. */
const route = useRoute()
const state = ref<'loading' | 'ok' | 'error'>('loading')
const message = ref('')

onMounted(async () => {
  const token = typeof route.params.token === 'string' ? route.params.token : ''
  if (!token) {
    state.value = 'error'
    message.value = 'В ссылке нет токена'
    return
  }
  try {
    await apiClient.request({
      method: 'POST',
      path: '/api/auth/verify-email',
      body: { token },
      auth: false,
    })
    state.value = 'ok'
  } catch (caught) {
    state.value = 'error'
    message.value = errorMessage(caught)
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card stack">
      <h1>Подтверждение почты</h1>
      <p v-if="state === 'loading'" class="muted">Проверяем ссылку…</p>
      <template v-else-if="state === 'ok'">
        <p class="notice notice--success">Почта подтверждена. Спасибо!</p>
        <RouterLink class="btn btn--primary btn--block" :to="{ name: 'login' }">Войти</RouterLink>
      </template>
      <template v-else>
        <p class="notice notice--danger">{{ message }}</p>
        <RouterLink class="btn btn--block" :to="{ name: 'login' }">Ко входу</RouterLink>
      </template>
    </div>
  </div>
</template>
