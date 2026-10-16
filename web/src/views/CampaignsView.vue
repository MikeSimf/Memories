<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import { MEMBER_ROLE_LABELS, type MemberRole } from '@/api/types'
import BaseModal from '@/components/BaseModal.vue'
import EmptyState from '@/components/EmptyState.vue'
import QrScannerDialog from '@/components/QrScannerDialog.vue'
import { useCampaignsStore } from '@/stores/campaigns'
import { useUiStore } from '@/stores/ui'
import { formatRelative } from '@/utils/datetime'

const campaigns = useCampaignsStore()
const ui = useUiStore()
const router = useRouter()

const createOpen = ref(false)
const title = ref('')
const description = ref('')
const creating = ref(false)
const scannerOpen = ref(false)
const joinOpen = ref(false)
const joinToken = ref('')

onMounted(() => {
  void campaigns.load()
})

async function create(): Promise<void> {
  if (title.value.trim() === '') return
  creating.value = true
  try {
    const record = await campaigns.create(title.value.trim(), description.value.trim())
    createOpen.value = false
    title.value = ''
    description.value = ''
    await router.push({
      name: 'campaign-tab',
      params: { campaignId: record.id, tab: 'characters' },
    })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    creating.value = false
  }
}

/** Токен из QR: ссылка `/join/TOKEN`, диплинк `dndmemory://join?token=TOKEN` или сам код. */
function extractToken(value: string): string | null {
  const trimmed = value.trim()
  const byPath = /\/join\/([A-Za-z0-9_-]+)/.exec(trimmed)
  if (byPath) return byPath[1]
  const byQuery = /[?&]token=([A-Za-z0-9_-]+)/.exec(trimmed)
  if (byQuery) return byQuery[1]
  return /^[A-Za-z0-9_-]{4,64}$/.test(trimmed) ? trimmed : null
}

function onQrDetected(value: string): void {
  scannerOpen.value = false
  const token = extractToken(value)
  if (!token) {
    ui.pushToast('error', 'В QR-коде нет токена приглашения')
    return
  }
  void router.push({ name: 'join', params: { token } })
}

/** Присоединение по коду приглашения: играет ту же роль, что и QR (`/join/:token`). */
async function joinByCode(): Promise<void> {
  const token = extractToken(joinToken.value)
  if (!token) {
    ui.pushToast('warning', 'Проверьте код приглашения')
    return
  }
  joinOpen.value = false
  joinToken.value = ''
  await router.push({ name: 'join', params: { token } })
}

const roleLabel = (role: MemberRole): string => MEMBER_ROLE_LABELS[role]
const hasOfflineData = computed(() => campaigns.loadedFromCache)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Кампании</h1>
        <div class="page-header__subtitle">
          {{ campaigns.items.length }} шт.
          <template v-if="hasOfflineData"> · данные с устройства</template>
        </div>
      </div>
      <button class="btn" type="button" @click="joinOpen = true">Присоединиться по коду</button>
      <button class="btn" type="button" @click="scannerOpen = true">Присоединиться по QR</button>
      <button class="btn btn--primary" type="button" @click="createOpen = true">Новая кампания</button>
    </div>

    <div v-if="!ui.online" class="notice notice--warning">
      Нет сети. Список кампаний показан из локальной базы, правки будут отправлены позже.
    </div>

    <div v-if="campaigns.loading" class="loading">
      <span class="spinner" aria-hidden="true" />
      Загружаем кампании…
    </div>

    <EmptyState
      v-else-if="campaigns.items.length === 0"
      title="Пока ни одной кампании"
      hint="Создайте кампанию или присоединитесь по QR-приглашению от мастера."
    >
      <button class="btn btn--primary" type="button" @click="createOpen = true">Создать кампанию</button>
    </EmptyState>

    <div v-else class="grid">
      <RouterLink
        v-for="item in campaigns.items"
        :key="item.id"
        class="card card--link"
        :to="{ name: 'campaign-tab', params: { campaignId: item.id, tab: 'characters' } }"
      >
        <h2 class="card__title">{{ item.title }}</h2>
        <p v-if="item.description" class="card__excerpt">{{ item.description }}</p>
        <div class="card__meta">
          <span class="badge badge--info">{{ roleLabel(item.myRole) }}</span>
          <span>{{ item.entityCount }} объектов</span>
          <span>{{ item.retellingCount }} пересказов</span>
          <span>{{ item.memberCount }} участников</span>
        </div>
        <div class="card__meta">
          <span>обновлено {{ formatRelative(item.updatedAt) }}</span>
        </div>
      </RouterLink>
    </div>

    <BaseModal :open="createOpen" title="Новая кампания" @close="createOpen = false">
      <form class="stack" @submit.prevent="create">
        <div class="field">
          <label class="field__label" for="campaign-title">Название</label>
          <input
            id="campaign-title"
            v-model="title"
            class="input"
            type="text"
            required
            placeholder="Курс на Фандалин"
          />
        </div>
        <div class="field">
          <label class="field__label" for="campaign-description">Описание</label>
          <textarea
            id="campaign-description"
            v-model="description"
            class="textarea"
            placeholder="Коротко о кампании"
          />
        </div>
        <div class="modal__actions">
          <button class="btn" type="button" @click="createOpen = false">Отмена</button>
          <button class="btn btn--primary" type="submit" :disabled="creating || title.trim() === ''">
            <span v-if="creating" class="spinner" aria-hidden="true" />
            Создать
          </button>
        </div>
      </form>
    </BaseModal>

    <BaseModal :open="joinOpen" title="Присоединиться по коду" @close="joinOpen = false">
      <form class="stack" @submit.prevent="joinByCode">
        <div class="field">
          <label class="field__label" for="join-token">Код приглашения</label>
          <input
            id="join-token"
            v-model="joinToken"
            class="input token-code"
            type="text"
            autocapitalize="characters"
            spellcheck="false"
            maxlength="8"
            placeholder="7F3K9Q2M"
          />
          <span class="field__hint">
            Восемь символов из QR-кода или ссылки приглашения. Приглашение одноразовое.
          </span>
        </div>
        <div class="modal__actions">
          <button class="btn" type="button" @click="joinOpen = false">Отмена</button>
          <button class="btn btn--primary" type="submit">Открыть приглашение</button>
        </div>
      </form>
    </BaseModal>

    <QrScannerDialog :open="scannerOpen" @close="scannerOpen = false" @detected="onQrDetected" />
  </div>
</template>
