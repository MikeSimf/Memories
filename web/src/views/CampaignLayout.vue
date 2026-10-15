<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import { MEMBER_ROLE_LABELS } from '@/api/types'
import { useCampaignStore } from '@/stores/campaign'
import { useSyncStore } from '@/stores/sync'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const campaign = useCampaignStore()
const sync = useSyncStore()
const ui = useUiStore()

const tabs = [
  { key: 'characters', label: 'Персонажи' },
  { key: 'locations', label: 'Локации' },
  { key: 'items', label: 'Предметы' },
] as const

/** Разделы кампании, вынесенные из вкладок (маршруты сохранены). */
const sections = [
  { key: 'retellings', label: 'Пересказы', route: 'campaign-retellings' },
  { key: 'members', label: 'Участники', route: 'campaign-members' },
] as const

const campaignId = computed(() => String(route.params.campaignId ?? ''))
const activeTab = computed(() => {
  const name = route.name
  if (name === 'campaign-retellings') return 'retellings'
  if (name === 'campaign-members') return 'members'
  return typeof route.params.tab === 'string' ? route.params.tab : ''
})
const roleLabel = computed(() =>
  campaign.campaign ? MEMBER_ROLE_LABELS[campaign.campaign.myRole] : '',
)

/** Объекты и пересказы, по которым сервер отклонил правку (`sync.md` §4). */
const conflicts = computed(() => [
  ...campaign.entities
    .filter((entity) => entity.conflict !== null && !entity.deleted)
    .map((entity) => ({ id: entity.id, name: entity.name, kind: 'entity' as const })),
  ...campaign.retellings
    .filter((retelling) => retelling.conflict !== null && !retelling.deleted)
    .map((retelling) => ({ id: retelling.id, name: retelling.title, kind: 'retelling' as const })),
])

watch(
  campaignId,
  async (id) => {
    if (!id) return
    await campaign.open(id)
    await sync.start(id)
    if (ui.online) {
      try {
        await campaign.reloadMembers()
      } catch (error) {
        console.warn('[dnd-memory] не удалось обновить участников', error)
      }
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  sync.stop()
  campaign.close()
})
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>{{ campaign.campaign?.title ?? 'Кампания' }}</h1>
        <div class="page-header__subtitle">
          <span v-if="roleLabel" class="badge badge--info">{{ roleLabel }}</span>
          <span v-if="campaign.campaign?.description"> · {{ campaign.campaign.description }}</span>
        </div>
      </div>
      <RouterLink
        class="btn btn--sm"
        :to="{ name: 'campaign-search', params: { campaignId } }"
        title="Общий поиск по кампании"
      >
        Поиск по кампании
      </RouterLink>
      <RouterLink
        v-for="section in sections"
        :key="section.key"
        class="btn btn--sm"
        :class="{ 'btn--primary': activeTab === section.key }"
        :to="{ name: section.route, params: { campaignId } }"
      >
        {{ section.label }}
      </RouterLink>
      <RouterLink
        v-if="campaign.canInvite || campaign.canManageMembers"
        class="btn btn--sm"
        :to="{ name: 'campaign-invites', params: { campaignId } }"
      >
        Приглашения
      </RouterLink>
      <RouterLink
        v-if="campaign.canManageCampaign"
        class="btn btn--sm"
        :to="{ name: 'campaign-settings', params: { campaignId } }"
      >
        Настройки
      </RouterLink>
    </div>

    <nav class="tabs" aria-label="Разделы кампании">
      <RouterLink
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ 'tab--active': activeTab === tab.key }"
        :to="{ name: 'campaign-tab', params: { campaignId, tab: tab.key } }"
      >
        {{ tab.label }}
      </RouterLink>
    </nav>

    <div v-if="conflicts.length > 0" class="notice notice--danger stack stack--sm">
      <strong>Есть расхождения с сервером: {{ conflicts.length }}</strong>
      <div class="row">
        <RouterLink
          v-for="item in conflicts"
          :key="item.id"
          class="btn btn--sm"
          :to="
            item.kind === 'entity'
              ? { name: 'entity-detail', params: { campaignId, entityId: item.id } }
              : { name: 'retelling-detail', params: { campaignId, retellingId: item.id } }
          "
        >
          {{ item.name }}
        </RouterLink>
      </div>
      <span class="faint">Откройте карточку и выберите: оставить серверную версию или перезаписать своей.</span>
    </div>

    <div v-if="campaign.loading" class="loading">
      <span class="spinner" aria-hidden="true" />
      Открываем кампанию…
    </div>

    <div v-else-if="!campaign.campaign" class="notice notice--warning">
      Кампания не найдена на устройстве. Подключитесь к сети, чтобы загрузить её.
    </div>

    <RouterView v-else />
  </div>
</template>
