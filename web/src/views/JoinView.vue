<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError, errorMessage, invitesApi, type InvitePreviewDto } from '@/api'
import { MEMBER_ROLE_LABELS } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const campaign = useCampaignStore()
const ui = useUiStore()

const token = computed(() => String(route.params.token ?? ''))
const preview = ref<InvitePreviewDto | null>(null)
const error = ref<string | null>(null)
const loading = ref(true)
const accepting = ref(false)

/**
 * `mode` различает два QR (`api.md` §3.1): `DEVICE` — код для мобильного
 * приложения, `MEMBER` — приглашение человеку. Браузер принимает только второе.
 */
const isDevice = computed(() => preview.value?.mode === 'DEVICE')
const deepLink = computed(() => `dndmemory://join?token=${encodeURIComponent(token.value)}`)

onMounted(async () => {
  try {
    const response = await invitesApi.preview(token.value)
    if (!response.valid) {
      error.value = 'Приглашение недействительно или просрочено'
    } else {
      preview.value = response
    }
  } catch (caught) {
    if (caught instanceof ApiError && (caught.code === 'invite_invalid' || caught.status === 404)) {
      error.value = 'Приглашение недействительно или просрочено'
    } else {
      error.value = errorMessage(caught)
    }
  } finally {
    loading.value = false
  }
})

async function accept(): Promise<void> {
  accepting.value = true
  error.value = null
  try {
    const response = await campaign.acceptInvite(token.value)
    ui.pushToast('success', 'Вы присоединились к кампании')
    await router.replace({
      name: 'campaign-tab',
      params: { campaignId: response.campaignId, tab: 'characters' },
    })
  } catch (caught) {
    if (caught instanceof ApiError && caught.code === 'invite_invalid') {
      error.value = 'Приглашение недействительно или просрочено'
    } else {
      error.value = errorMessage(caught)
    }
  } finally {
    accepting.value = false
  }
}

function loginLink() {
  return { name: 'login', query: { redirect: `/join/${token.value}` } }
}

function registerLink() {
  return { name: 'register', query: { redirect: `/join/${token.value}` } }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div v-if="loading" class="loading"><span class="spinner" aria-hidden="true" /> Проверяем приглашение…</div>

      <template v-else-if="error">
        <h1>Приглашение недоступно</h1>
        <p class="notice notice--danger">{{ error }}</p>
        <RouterLink class="btn btn--primary btn--block" :to="{ name: 'campaigns' }">К кампаниям</RouterLink>
      </template>

      <template v-else-if="preview">
        <div class="auth-card__title">
          <img src="/icons/icon-192.svg" alt="" width="40" height="40" />
          <div>
            <h1 style="margin: 0">{{ preview.campaignTitle }}</h1>
            <span class="faint">
              {{ isDevice ? 'Код регистрации устройства' : 'Приглашение в кампанию DnD Memory' }}
            </span>
          </div>
        </div>

        <!-- QR для телефона: браузер приглашение не принимает -->
        <template v-if="isDevice">
          <div class="notice notice--accent">
            Это код для мобильного приложения — откройте DnD Memory и отсканируйте QR.
          </div>
          <div class="stack stack--sm" style="margin-top: 0.75rem">
            <div class="faint">
              Для кого: {{ preview.displayName || 'новый игрок' }} ·
              {{ MEMBER_ROLE_LABELS[preview.role] }}
            </div>
            <div class="faint">Код действует до {{ formatDateTime(preview.expiresAt) }}</div>
            <div class="token-code">{{ token }}</div>
          </div>
          <a class="btn btn--primary btn--block" :href="deepLink" style="margin-top: 1rem">
            Открыть приложение
          </a>
          <p class="faint center" style="margin-top: 0.75rem">
            Открывать эту ссылку в браузере не нужно — она запускает приложение на телефоне.
          </p>
        </template>

        <template v-else>
          <div class="stack stack--sm">
            <div class="row">
              <span class="badge badge--info">{{ MEMBER_ROLE_LABELS[preview.role] }}</span>
              <span v-if="preview.linkedEntityName" class="faint">
                роль привязана к герою «{{ preview.linkedEntityName }}»
              </span>
            </div>
            <div class="faint">Действует до {{ formatDateTime(preview.expiresAt) }}</div>
          </div>

          <template v-if="auth.isAuthenticated">
            <button
              class="btn btn--primary btn--block"
              type="button"
              style="margin-top: 1rem"
              :disabled="accepting"
              @click="accept"
            >
              <span v-if="accepting" class="spinner" aria-hidden="true" />
              Присоединиться
            </button>
          </template>
          <template v-else>
            <div class="stack stack--sm" style="margin-top: 1rem">
              <RouterLink class="btn btn--primary btn--block" :to="loginLink()">Войти и присоединиться</RouterLink>
              <RouterLink class="btn btn--block" :to="registerLink()">Зарегистрироваться</RouterLink>
            </div>
          </template>
        </template>

        <p class="faint center" style="margin-top: 0.75rem">
          <RouterLink :to="{ name: 'campaigns' }">Открыть мои кампании</RouterLink>
        </p>
      </template>

      <EmptyState v-else title="Нет данных о приглашении" />
    </div>
  </div>
</template>
