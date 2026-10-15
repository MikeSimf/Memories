<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { OAuthProvider, OAuthProviderInfo } from '@/api/types'
import { errorMessage } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const email = ref('')
const password = ref('')
const submitting = ref(false)

const oauthProviders = computed(() => {
  const providers = auth.providers
  if (!providers) return []
  return (Object.entries(providers) as Array<[OAuthProvider, OAuthProviderInfo]>)
    .filter(([, info]) => info && info.enabled)
    .map(([key]) => ({ key, label: key === 'yandex' ? 'Войти через Яндекс' : 'Войти через VK' }))
})

onMounted(() => {
  void auth.loadProviders()
})

function redirectTarget(): string | { name: string } {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : { name: 'campaigns' }
}

async function submit(): Promise<void> {
  submitting.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    ui.pushToast('success', 'Добро пожаловать!')
    await router.replace(redirectTarget())
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    submitting.value = false
  }
}

async function startOAuth(provider: OAuthProvider): Promise<void> {
  const url = await auth.startOAuth(provider)
  window.location.href = url
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
          <label class="field__label" for="login-email">Почта</label>
          <input
            id="login-email"
            v-model="email"
            class="input"
            type="email"
            autocomplete="email"
            required
            placeholder="gm@example.com"
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
            minlength="8"
          />
        </div>
        <button class="btn btn--primary btn--block" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true" />
          Войти
        </button>
      </form>

      <template v-if="oauthProviders.length > 0">
        <div class="divider">или</div>
        <div class="stack stack--sm">
          <button
            v-for="provider in oauthProviders"
            :key="provider.key"
            class="btn btn--block"
            type="button"
            @click="startOAuth(provider.key)"
          >
            {{ provider.label }}
          </button>
        </div>
      </template>

      <p class="muted center" style="margin-top: 1rem">
        Нет аккаунта?
        <RouterLink :to="{ name: 'register', query: route.query }">Зарегистрироваться</RouterLink>
      </p>
    </div>
  </div>
</template>
