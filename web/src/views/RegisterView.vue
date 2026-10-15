<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import type { OAuthProvider, OAuthProviderInfo } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const email = ref('')
const password = ref('')
const passwordAgain = ref('')
const displayName = ref('')
const submitting = ref(false)
const localError = ref<string | null>(null)

const oauthProviders = computed(() => {
  const providers = auth.providers
  if (!providers) return []
  return (Object.entries(providers) as Array<[OAuthProvider, OAuthProviderInfo]>)
    .filter(([, info]) => info && info.enabled)
    .map(([key]) => ({ key, label: key === 'yandex' ? 'Зарегистрироваться через Яндекс' : 'Через VK' }))
})

onMounted(() => {
  void auth.loadProviders()
})

function redirectTarget(): string | { name: string } {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : { name: 'campaigns' }
}

async function submit(): Promise<void> {
  localError.value = null
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
    await auth.register(email.value.trim(), password.value, displayName.value.trim())
    ui.pushToast('success', 'Аккаунт создан')
    await router.replace(redirectTarget())
  } catch (caught) {
    localError.value = errorMessage(caught)
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
        <h1 style="margin: 0">Регистрация</h1>
      </div>

      <form class="stack" @submit.prevent="submit">
        <div class="field">
          <label class="field__label" for="reg-name">Как вас зовут</label>
          <input
            id="reg-name"
            v-model="displayName"
            class="input"
            type="text"
            autocomplete="nickname"
            required
            placeholder="Мастер"
          />
        </div>
        <div class="field">
          <label class="field__label" for="reg-email">Почта</label>
          <input id="reg-email" v-model="email" class="input" type="email" autocomplete="email" required />
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
        Уже есть аккаунт?
        <RouterLink :to="{ name: 'login', query: route.query }">Войти</RouterLink>
      </p>
    </div>
  </div>
</template>
