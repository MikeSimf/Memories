<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { dictionariesApi, errorMessage, type DictionaryEntryDto } from '@/api'
import MembersPanel from '@/components/MembersPanel.vue'
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
const dictionaries = ref<DictionaryEntryDto[]>([])
const dictionaryKind = ['RACE', 'ITEM_TYPE', 'LOCATION_TYPE'] as const
const dictionaryTitles: Record<string, string> = {
  RACE: 'Расы',
  ITEM_TYPE: 'Типы предметов',
  LOCATION_TYPE: 'Типы локаций',
}

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
const canDelete = computed(() => campaign.canDeleteCampaign)
const campaignId = computed(() => campaign.campaignId ?? '')
const section = ref<'main' | 'members' | 'dictionaries' | 'sync' | 'danger'>('main')

watch(campaignId, () => { if (campaignId.value) void loadDictionaries() }, { immediate: true })

async function loadDictionaries(): Promise<void> {
  try {
    dictionaries.value = await dictionariesApi.list(campaignId.value)
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  }
}

async function toggleDictionary(kind: string, entry: DictionaryEntryDto): Promise<void> {
  try {
    await dictionariesApi.set(campaignId.value, kind, entry.name, !entry.enabled)
    await loadDictionaries()
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  }
}

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

    <div class="settings-layout">
      <aside class="settings-nav">
        <button class="settings-nav__item" :class="{ 'settings-nav__item--active': section === 'main' }" type="button" @click="section = 'main'">Основное</button>
        <button class="settings-nav__item" :class="{ 'settings-nav__item--active': section === 'members' }" type="button" @click="section = 'members'">Участники</button>
        <button class="settings-nav__item" :class="{ 'settings-nav__item--active': section === 'dictionaries' }" type="button" @click="section = 'dictionaries'">Справочники</button>
        <button class="settings-nav__item" :class="{ 'settings-nav__item--active': section === 'sync' }" type="button" @click="section = 'sync'">Синхронизация</button>
        <button class="settings-nav__item" :class="{ 'settings-nav__item--active': section === 'danger' }" type="button" @click="section = 'danger'">Действия</button>
      </aside>
      <div class="settings-main stack">
    <form v-if="section === 'main'" class="card stack" @submit.prevent="save">
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

    <div v-if="section === 'sync'" class="card stack">
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

    <MembersPanel v-if="section === 'members'" />

    <div v-if="section === 'dictionaries' && canManage" class="card stack">
      <h2>Справочники</h2>
      <p class="muted">Снятые галки скрывают значение при создании. Используемые значения снять нельзя.</p>
      <div v-for="kind in dictionaryKind" :key="kind" class="stack stack--sm">
        <strong>{{ dictionaryTitles[kind] }}</strong>
        <label
          v-for="entry in dictionaries.filter((item) => item.kind === kind)"
          :key="entry.name"
          class="row"
        >
          <input
            type="checkbox"
            :checked="entry.enabled"
            :disabled="entry.used"
            @change="toggleDictionary(kind, entry)"
          />
          {{ entry.name }} <span v-if="entry.used" class="faint">— используется</span>
        </label>
      </div>
    </div>

    <div v-if="section === 'danger' && canManage" class="card stack">
      <h2>Действия</h2>
      <p class="muted">Удаление кампании и другие необратимые действия.</p>
      <p class="muted">
        Кампания удаляется на сервере для всех участников. Действие необратимо.
      </p>
      <div v-if="canDelete" class="row">
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
    </div>
  </div>
</template>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.settings-nav {
  position: sticky;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px;
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.settings-nav__item {
  text-align: left;
  border: 0;
  background: transparent;
  color: inherit;
  padding: 9px 11px;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
}
.settings-nav__item:hover { background: rgba(127, 127, 127, .12); }
.settings-nav__item--active { background: var(--accent); color: #fff; }
.settings-main { min-width: 0; }
@media (max-width: 760px) {
  .settings-layout { grid-template-columns: 1fr; }
  .settings-nav { position: static; flex-direction: row; overflow-x: auto; }
  .settings-nav__item { white-space: nowrap; }
}
</style>
