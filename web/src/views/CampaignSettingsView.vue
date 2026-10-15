<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { errorMessage } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { clearCampaignData } from '@/db'
import { useCampaignStore } from '@/stores/campaign'
import { useCampaignsStore } from '@/stores/campaigns'
import { useSyncStore } from '@/stores/sync'
import { useUiStore } from '@/stores/ui'
import { formatDateTime } from '@/utils/datetime'

const router = useRouter()
const campaign = useCampaignStore()
const campaigns = useCampaignsStore()
const sync = useSyncStore()
const ui = useUiStore()

const title = ref('')
const description = ref('')
const saving = ref(false)
const confirmDelete = ref(false)
const busy = ref(false)

watch(
  () => campaign.campaign,
  (record) => {
    if (record) {
      title.value = record.title
      description.value = record.description
    }
  },
  { immediate: true },
)

const canManage = computed(() => campaign.canManageCampaign)
const campaignId = computed(() => campaign.campaignId ?? '')

async function save(): Promise<void> {
  saving.value = true
  try {
    await campaigns.update(campaignId.value, title.value.trim(), description.value.trim())
    await campaign.reload()
    ui.pushToast('success', 'Кампания обновлена')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    saving.value = false
  }
}

async function syncNow(): Promise<void> {
  await sync.syncNow()
  await campaign.reload()
}

async function reloadFromServer(): Promise<void> {
  await sync.reloadFull()
  await campaign.reload()
}

async function clearLocal(): Promise<void> {
  if (!window.confirm('Удалить локальные данные кампании на этом устройстве?')) return
  busy.value = true
  try {
    await clearCampaignData(campaignId.value)
    ui.pushToast('success', 'Локальные данные очищены — они загрузятся заново при синхронизации')
    await campaign.reload()
    await sync.reloadFull()
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}

async function removeCampaign(): Promise<void> {
  busy.value = true
  try {
    await campaigns.remove(campaignId.value)
    confirmDelete.value = false
    ui.pushToast('success', 'Кампания удалена')
    await router.push({ name: 'campaigns' })
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Настройки кампании</h1>
        <div class="page-header__subtitle">{{ campaign.campaign?.title }}</div>
      </div>
    </div>

    <form class="card stack" @submit.prevent="save">
      <h2>Название и описание</h2>
      <div class="field">
        <label class="field__label" for="settings-title">Название</label>
        <input id="settings-title" v-model="title" class="input" type="text" :disabled="!canManage" />
      </div>
      <div class="field">
        <label class="field__label" for="settings-description">Описание</label>
        <textarea
          id="settings-description"
          v-model="description"
          class="textarea"
          :disabled="!canManage"
        />
      </div>
      <p v-if="!canManage" class="notice">
        Менять кампанию может только владелец (право <code>campaign.manage</code>).
      </p>
      <div v-else class="row row--end">
        <button class="btn btn--primary" type="submit" :disabled="saving">
          <span v-if="saving" class="spinner" aria-hidden="true" />
          Сохранить
        </button>
      </div>
    </form>

    <div class="card stack">
      <h2>Синхронизация</h2>
      <div class="faint">
        <div>Кампания: {{ campaign.campaign?.entityCount ?? 0 }} объектов, {{ campaign.campaign?.retellingCount ?? 0 }} пересказов, {{ campaign.campaign?.memberCount ?? 0 }} участников.</div>
        <div>Последняя ревизия на устройстве: {{ sync.status?.lastPulledRevision ?? 0 }}</div>
        <div>Не отправлено правок: {{ sync.pendingCount }}</div>
        <div>Расхождений: {{ sync.conflictCount }}</div>
        <div>Последняя синхронизация: {{ formatDateTime(sync.lastSyncAt) }}</div>
        <div v-if="sync.lastError" class="text-danger">Ошибка: {{ sync.lastError }}</div>
      </div>
      <div class="row">
        <button class="btn" type="button" :disabled="sync.busy" @click="syncNow">
          Синхронизировать сейчас
        </button>
        <button class="btn" type="button" :disabled="busy || sync.busy" @click="reloadFromServer">
          Полная перезагрузка данных
        </button>
        <button class="btn btn--danger" type="button" :disabled="busy" @click="clearLocal">
          Очистить локальные данные
        </button>
      </div>
      <p class="faint">
        Полная перезагрузка запрашивает snapshot кампании — это же действие выполняется
        автоматически, если журнал изменений на сервере уже скомпактирован.
      </p>
    </div>

    <div v-if="canManage" class="card stack">
      <h2>Удаление кампании</h2>
      <p class="muted">
        Кампания удаляется на сервере для всех участников. Действие необратимо.
      </p>
      <div class="row">
        <button class="btn btn--danger" type="button" @click="confirmDelete = true">
          Удалить кампанию
        </button>
      </div>
    </div>

    <ConfirmDialog
      :open="confirmDelete"
      :busy="busy"
      title="Удалить кампанию?"
      :message="`«${campaign.campaign?.title ?? ''}» будет удалена вместе со всеми объектами и пересказами.`"
      confirm-label="Удалить кампанию"
      @cancel="confirmDelete = false"
      @confirm="removeCampaign"
    />
  </div>
</template>
