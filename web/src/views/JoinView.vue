<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError, errorMessage, invitesApi, type InvitePreviewDto } from '@/api'
import { MEMBER_ROLE_LABELS } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const campaign = useCampaignStore()
const ui = useUiStore()

const token = computed(() => String(route.params.token ?? '').trim())
const preview = ref<InvitePreviewDto | null>(null)
const error = ref<string | null>(null)
const loading = ref(true)
const busy = ref(false)

/** Приглашение строго одноразовое: повторная попытка даёт `409 invite_invalid`. */
const INVITE_INVALID = 'Приглашение уже использовано или просрочено'

const mode = ref<'register' | 'login'>('register')
const regLogin = ref('')
const regPassword = ref('')
const regName = ref('')
const loginName = ref('')
const loginPassword = ref('')

onMounted(async () => {
  try {
    const response = await invitesApi.preview(token.value)
    if (!response.valid) {
      error.value = INVITE_INVALID
    } else {
      preview.value = response
    }
  } catch (caught) {
    if (caught instanceof ApiError && (caught.code === 'invite_invalid' || caught.status === 404)) {
      error.value = INVITE_INVALID
    } else {
      error.value = errorMessage(caught)
    }
  } finally {
    loading.value = false
  }
})

function inviteError(caught: unknown): string {
  if (caught instanceof ApiError && caught.code === 'invite_invalid') return INVITE_INVALID
  return errorMessage(caught)
}

async function openCampaign(campaignId: string): Promise<void> {
  await router.replace({
    name: 'campaign-tab',
    params: { campaignId, tab: 'characters' },
  })
}

/** Вход существующим аккаунтом, затем принятие приглашения. */
async function loginAndAccept(): Promise<void> {
  busy.value = true
  error.value = null
  try {
    await auth.login(loginName.value.trim(), loginPassword.value)
    const response = await campaign.acceptInvite(token.value)
    ui.pushToast('success', 'Вы присоединились к кампании')
    await openCampaign(response.campaignId)
  } catch (caught) {
    error.value = inviteError(caught)
  } finally {
    busy.value = false
  }
}

/** Регистрация с `inviteToken`: пользователь создаётся и сразу попадает в кампанию. */
async function registerAndJoin(): Promise<void> {
  busy.value = true
  error.value = null
  try {
    if (!/^[A-Za-z0-9_.-]{3,32}$/.test(regLogin.value.trim())) {
      error.value = 'Логин: 3–32 символа из латинских букв, цифр, «_», «.» и «-»'
      return
    }
    if (regPassword.value.length < 8) {
      error.value = 'Пароль должен быть не короче 8 символов'
      return
    }
    await auth.register({
      login: regLogin.value.trim(),
      password: regPassword.value,
      displayName: regName.value.trim(),
      inviteToken: token.value,
    })
    ui.pushToast('success', 'Аккаунт создан, вы в кампании')
    const campaignId = preview.value?.campaignId
    if (campaignId) {
      await openCampaign(campaignId)
    } else {
      await router.replace({ name: 'campaigns' })
    }
  } catch (caught) {
    error.value = inviteError(caught)
  } finally {
    busy.value = false
  }
}

/** Уже вошедший пользователь принимает приглашение одной кнопкой. */
async function accept(): Promise<void> {
  busy.value = true
  error.value = null
  try {
    const response = await campaign.acceptInvite(token.value)
    ui.pushToast('success', 'Вы присоединились к кампании')
    await openCampaign(response.campaignId)
  } catch (caught) {
    error.value = inviteError(caught)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div v-if="loading" class="loading"><span class="spinner" aria-hidden="true" /> Проверяем приглашение…</div>

      <template v-else>
        <div class="auth-card__title">
          <img src="/icons/icon-192.svg" alt="" width="40" height="40" />
          <div>
            <h1 style="margin: 0">{{ preview?.campaignTitle || 'Приглашение в кампанию' }}</h1>
            <span class="faint">DnD Memory</span>
          </div>
        </div>

        <div v-if="preview" class="stack stack--sm">
          <div class="row">
            <span class="badge badge--info">{{ MEMBER_ROLE_LABELS[preview.role] }}</span>
            <span v-if="preview.linkedHeroName" class="faint">
              роль привязана к герою «{{ preview.linkedHeroName }}»
            </span>
          </div>
          <div class="faint">Действует до {{ formatDateTime(preview.expiresAt) }}</div>
          <div class="faint">Приглашение одноразовое: после присоединения ссылка перестанет работать.</div>
        </div>
        <p v-else-if="error" class="notice notice--warning">
          Не удалось проверить приглашение: {{ error }}. Войдите или зарегистрируйтесь — приглашение
          применится после входа.
        </p>

        <p v-if="error" class="notice notice--danger" style="margin-top: 1rem">{{ error }}</p>

        <!-- Уже вошли: принимаем приглашение сразу -->
        <template v-if="auth.isAuthenticated">
          <button
            class="btn btn--primary btn--block"
            type="button"
            style="margin-top: 1rem"
            :disabled="busy"
            @click="accept"
          >
            <span v-if="busy" class="spinner" aria-hidden="true" />
            Присоединиться
          </button>
        </template>

        <template v-else>
          <div class="tabs" style="margin-top: 1rem">
            <button
              class="tab"
              :class="{ 'tab--active': mode === 'register' }"
              type="button"
              @click="mode = 'register'"
            >
              Я новый игрок
            </button>
            <button
              class="tab"
              :class="{ 'tab--active': mode === 'login' }"
              type="button"
              @click="mode = 'login'"
            >
              У меня есть аккаунт
            </button>
          </div>

          <form v-if="mode === 'register'" class="stack" @submit.prevent="registerAndJoin">
            <div class="field">
              <label class="field__label" for="join-login">Логин</label>
              <input
                id="join-login"
                v-model="regLogin"
                class="input"
                type="text"
                autocomplete="username"
                autocapitalize="none"
                spellcheck="false"
                required
                minlength="3"
                maxlength="32"
              />
            </div>
            <div class="field">
              <label class="field__label" for="join-name">Отображаемое имя</label>
              <input id="join-name" v-model="regName" class="input" type="text" autocomplete="nickname" required />
            </div>
            <div class="field">
              <label class="field__label" for="join-password">Пароль</label>
              <input
                id="join-password"
                v-model="regPassword"
                class="input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
              />
              <span class="field__hint">Не короче 8 символов.</span>
            </div>
            <button class="btn btn--primary btn--block" type="submit" :disabled="busy">
              <span v-if="busy" class="spinner" aria-hidden="true" />
              Зарегистрироваться и присоединиться
            </button>
          </form>

          <form v-else class="stack" @submit.prevent="loginAndAccept">
            <div class="field">
              <label class="field__label" for="join-login-name">Логин</label>
              <input
                id="join-login-name"
                v-model="loginName"
                class="input"
                type="text"
                autocomplete="username"
                autocapitalize="none"
                spellcheck="false"
                required
              />
            </div>
            <div class="field">
              <label class="field__label" for="join-login-password">Пароль</label>
              <input
                id="join-login-password"
                v-model="loginPassword"
                class="input"
                type="password"
                autocomplete="current-password"
                required
              />
            </div>
            <button class="btn btn--primary btn--block" type="submit" :disabled="busy">
              <span v-if="busy" class="spinner" aria-hidden="true" />
              Войти и присоединиться
            </button>
          </form>
        </template>

        <p class="faint center" style="margin-top: 0.75rem">
          <RouterLink :to="{ name: 'campaigns' }">Открыть мои кампании</RouterLink>
        </p>
      </template>
    </div>
  </div>
</template>
