<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import type { OAuthProvider } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const error = ref<string | null>(null)

/**
 * Возврат от провайдера (`api.md` §3): провайдер присылает `code` на
 * `/oauth/callback`, клиент передаёт его на сервер и получает токены.
 */
onMounted(async () => {
  const provider = (route.query.provider as OAuthProvider | undefined) ?? 'yandex'
  const code = typeof route.query.code === 'string' ? route.query.code : null
  const state = typeof route.query.state === 'string' ? route.query.state : null
  const providerError = typeof route.query.error === 'string' ? route.query.error : null

  try {
    await auth.completeOAuth(provider, { code, state, error: providerError })
    await router.replace({ name: 'campaigns' })
  } catch (caught) {
    error.value = errorMessage(caught)
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card center">
      <template v-if="!error">
        <div class="loading">
          <span class="spinner" aria-hidden="true" />
          Завершаем вход…
        </div>
      </template>
      <template v-else>
        <h1>Не удалось войти</h1>
        <p class="notice notice--danger">{{ error }}</p>
        <RouterLink class="btn btn--primary" :to="{ name: 'login' }">Вернуться к входу</RouterLink>
      </template>
    </div>
  </div>
</template>
