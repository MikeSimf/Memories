<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import { MEMBER_ROLE_LABELS } from '@/api/types'
import { useCampaignStore } from '@/stores/campaign'
import { useSyncStore } from '@/stores/sync'

const route = useRoute()
const campaign = useCampaignStore()
const sync = useSyncStore()

/** Основные рабочие вкладки: персонажи, локации, предметы. */
const tabs = [
  { key: 'characters', label: 'Персонажи', route: 'campaign-tab' },
  { key: 'locations', label: 'Локации', route: 'campaign-tab' },
  { key: 'items', label: 'Предметы', route: 'campaign-tab' },
] as const

/** Остальные разделы кампании — иконками в шапке, как в приложении. */
const sections = [
  { key: 'heroes', label: 'Герои', route: 'campaign-heroes' },
  { key: 'retellings', label: 'Пересказы', route: 'campaign-retellings' },
  { key: 'materials', label: 'Материалы', route: 'campaign-materials' },
  { key: 'members', label: 'Участники', route: 'campaign-members' },
] as const

const campaignId = computed(() => String(route.params.campaignId ?? ''))
const activeTab = computed(() => {
  const name = route.name
  if (name === 'campaign-heroes') return 'heroes'
  if (name === 'campaign-retellings') return 'retellings'
  if (name === 'campaign-materials' || name === 'material-new' || name === 'material-edit') return 'materials'
  if (name === 'campaign-members') return 'members'
  return typeof route.params.tab === 'string' ? route.params.tab : ''
})
const roleLabel = computed(() =>
  campaign.campaign ? MEMBER_ROLE_LABELS[campaign.campaign.myRole] : '',
)

/** Объекты и пересказы, по которым сервер отклонил правку (`sync.md` §5). */
const conflicts = computed(() => [
  ...campaign.objects
    .filter((object) => object.conflict !== null && !object.deleted)
    .map((object) => ({ id: object.id, name: object.name, kind: 'object' as const })),
  ...campaign.retellings
    .filter((retelling) => retelling.conflict !== null && !retelling.deleted)
    .map((retelling) => ({ id: retelling.id, name: retelling.title, kind: 'retelling' as const })),
])

watch(
  campaignId,
  async (id) => {
    if (!id) return
    // Участники и краткие пересказы приходят в карточке кампании (`api.md` §4),
    // поэтому отдельных запросов за ними здесь нет.
    await campaign.open(id)
    await sync.start(id)
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
        class="btn btn--icon"
        :class="{ 'btn--primary': activeTab === section.key }"
        :to="{ name: section.route, params: { campaignId } }"
        :title="section.label"
        :aria-label="section.label"
      >
        <svg
          v-if="section.key === 'heroes'"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="9" r="3.2" />
          <path d="M7 6.6C7.8 3.8 9.6 2.4 12 2.4s4.2 1.4 5 4.2M8.4 6.6h7.2" />
          <path d="M5 20c0-3.4 3.1-5.8 7-5.8s7 2.4 7 5.8" />
        </svg>
        <svg
          v-else-if="section.key === 'retellings'"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M4 5.2h5.2c1.4 0 2.4 1 2.4 2.4v12.2c0-1.2-1-2.2-2.4-2.2H4zM20 5.2h-5.2c-1.4 0-2.4 1-2.4 2.4v12.2c0-1.2 1-2.2 2.4-2.2H20z" />
          <path d="M16.8 2.4l2.2 2.2-4.6 4.6-2.6.4.4-2.6z" />
        </svg>
        <svg
          v-else-if="section.key === 'materials'"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M4 6.2h13.6v3.4H4zM4 11h16v3.4H4zM4 15.8h11.6v3.4H4z" />
          <path d="M17.6 6.2h2.2M20 11h.4M15.6 15.8h2.2" />
        </svg>
        <svg
          v-else
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="8" r="3.6" />
          <path d="M5 20.4c0-3.6 3.1-6 7-6s7 2.4 7 6" />
        </svg>
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
        :to="{ name: tab.route, params: { campaignId, tab: tab.key } }"
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
            item.kind === 'object'
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
