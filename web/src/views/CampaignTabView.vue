<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  ENTITY_TYPE_LABELS,
  ENTITY_TYPE_PLURALS,
  RETELLING_STATUS_LABELS,
  type EntityType,
} from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import MembersPanel from '@/components/MembersPanel.vue'
import SearchBox from '@/components/SearchBox.vue'
import { matchesText } from '@/db/search'
import { useCampaignStore } from '@/stores/campaign'
import { formatDate, formatRelative } from '@/utils/datetime'

type TabKey = 'heroes' | 'npcs' | 'locations' | 'items' | 'retellings' | 'members'

const TAB_TYPE: Record<string, EntityType> = {
  heroes: 'HERO',
  npcs: 'NPC',
  locations: 'LOCATION',
  items: 'ITEM',
}

const route = useRoute()
const campaign = useCampaignStore()

const query = ref('')
const tab = computed<TabKey>(() => (route.params.tab as TabKey) ?? 'heroes')
const entityType = computed<EntityType | null>(() => TAB_TYPE[tab.value] ?? null)

// Строка поиска внутри вкладки — своя для каждой вкладки.
watch(tab, () => {
  query.value = ''
})

const entities = computed(() =>
  entityType.value
    ? campaign.activeEntities.filter((entity) => entity.type === entityType.value)
    : [],
)

const filteredEntities = computed(() =>
  entities.value.filter((entity) =>
    matchesText(
      [entity.name, entity.summary, entity.description, entity.tags.join(' ')],
      query.value,
    ),
  ),
)

const filteredRetellings = computed(() =>
  campaign.retellingItems.filter((item) => matchesText([item.title, item.body], query.value)),
)

const sectionTitle = computed(() => {
  if (entityType.value) return ENTITY_TYPE_PLURALS[entityType.value]
  if (tab.value === 'retellings') return 'Пересказы'
  return 'Участники'
})

const canCreateHere = computed(() =>
  entityType.value ? campaign.canCreate(entityType.value) : false,
)
</script>

<template>
  <section class="stack">
    <div class="row row--between">
      <h2 style="margin: 0">{{ sectionTitle }}</h2>
      <div class="row">
        <SearchBox
          v-if="tab !== 'members'"
          v-model="query"
          :placeholder="`Поиск: ${sectionTitle.toLowerCase()}`"
        />
        <RouterLink
          v-if="entityType && canCreateHere"
          class="btn btn--primary btn--sm"
          :to="{ name: 'entity-new', params: { campaignId: campaign.campaignId }, query: { type: entityType } }"
        >
          + {{ ENTITY_TYPE_LABELS[entityType] }}
        </RouterLink>
        <RouterLink
          v-if="tab === 'retellings' && campaign.canWriteRetellings"
          class="btn btn--primary btn--sm"
          :to="{ name: 'retelling-new', params: { campaignId: campaign.campaignId } }"
        >
          + Пересказ
        </RouterLink>
      </div>
    </div>

    <!-- Объекты кампании: герои, персонажи, локации, предметы -->
    <template v-if="entityType">
      <EmptyState
        v-if="entities.length === 0"
        :title="`Пока нет: ${sectionTitle.toLowerCase()}`"
        :hint="
          canCreateHere
            ? 'Создайте первую запись — она сохранится на устройстве и уйдёт на сервер при синхронизации.'
            : 'У вас нет прав на создание объектов этого типа.'
        "
      />
      <EmptyState
        v-else-if="filteredEntities.length === 0"
        title="Ничего не найдено"
        hint="Попробуйте изменить запрос — поиск идёт по локальной базе и работает без сети."
      />
      <div v-else class="grid">
        <RouterLink
          v-for="entity in filteredEntities"
          :key="entity.id"
          class="card card--link"
          :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: entity.id } }"
        >
          <h3 class="card__title">
            {{ entity.name }}
            <span v-if="entity.dirty === 1" class="dirty-dot" title="Есть неотправленная правка" />
          </h3>
          <p v-if="entity.summary" class="card__excerpt">{{ entity.summary }}</p>
          <div v-if="entity.tags.length > 0" class="chips" style="margin-top: 0.4rem">
            <span v-for="tag in entity.tags" :key="tag" class="chip">{{ tag }}</span>
          </div>
          <div class="card__meta">
            <span class="badge" :class="`badge--${entity.type.toLowerCase()}`">
              {{ ENTITY_TYPE_LABELS[entity.type] }}
            </span>
            <span v-if="entity.conflict" class="badge badge--danger">расхождение</span>
            <span>{{ campaign.changeSummary(entity) }}</span>
          </div>
        </RouterLink>
      </div>
    </template>

    <!-- Пересказы: черновики и публикации -->
    <template v-else-if="tab === 'retellings'">
      <EmptyState
        v-if="campaign.retellingItems.length === 0"
        title="Пересказов пока нет"
        hint="Черновик хранится только на устройстве. На сервер пересказ уходит после нажатия «Опубликовать»."
      />
      <EmptyState v-else-if="filteredRetellings.length === 0" title="Ничего не найдено" />
      <div v-else class="list">
        <RouterLink
          v-for="item in filteredRetellings"
          :key="item.id"
          class="list-row"
          :to="{ name: 'retelling-detail', params: { campaignId: campaign.campaignId, retellingId: item.id } }"
        >
          <div class="list-row__main">
            <div class="list-row__title">
              {{ item.title || 'Без названия' }}
              <span
                class="badge"
                :class="item.status === 'DRAFT' ? 'badge--warning' : 'badge--success'"
              >
                {{ RETELLING_STATUS_LABELS[item.status] }}
              </span>
              <span v-if="item.dirty" class="dirty-dot" title="Есть неотправленная правка" />
              <span v-if="item.conflicted" class="badge badge--danger">расхождение</span>
            </div>
            <div class="faint">
              <span v-if="item.sessionDate">сессия {{ formatDate(item.sessionDate) }} · </span>
              <span v-if="item.authorEntityName">от лица {{ item.authorEntityName }} · </span>
              {{ item.updatedBy }}, {{ formatRelative(item.updatedAt) }}
            </div>
          </div>
        </RouterLink>
      </div>
    </template>

    <!-- Участники -->
    <template v-else>
      <MembersPanel />
    </template>
  </section>
</template>
