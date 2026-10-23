<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  OBJECT_CLASS_LABELS,
  OBJECT_CLASS_PLURALS,
  RETELLING_STATUS_LABELS,
  type ObjectClass,
} from '@/api/types'
import type { LocalObject } from '@/db/schema'
import EmptyState from '@/components/EmptyState.vue'
import EntityAvatar from '@/components/EntityAvatar.vue'
import MembersPanel from '@/components/MembersPanel.vue'
import SearchBox from '@/components/SearchBox.vue'
import { genderSymbol } from '@/types/gender'
import { matchesText, objectMatchesQuery } from '@/db/search'
import { useCampaignStore } from '@/stores/campaign'
import { formatDate, formatRelative } from '@/utils/datetime'

/**
 * Основные рабочие вкладки: персонажи (только НПС), локации и предметы.
 * Герои, истории, материалы и участники — отдельные разделы кампании
 * в шапке, поэтому у каждого списка ровно один класс.
 */
type EntityTabKey = 'characters' | 'heroes' | 'locations' | 'items'
type TabKey = EntityTabKey | 'retellings' | 'members'

const TAB_CLASSES: Record<EntityTabKey, ObjectClass[]> = {
  characters: ['CHARACTER'],
  heroes: ['HERO'],
  locations: ['LOCATION'],
  items: ['ITEM'],
}

const TAB_TITLES: Record<EntityTabKey, string> = {
  characters: 'Персонажи',
  heroes: 'Герои',
  locations: OBJECT_CLASS_PLURALS.LOCATION,
  items: OBJECT_CLASS_PLURALS.ITEM,
}

const props = defineProps<{ tab?: string }>()

const route = useRoute()
const campaign = useCampaignStore()

const query = ref('')
const tab = computed<TabKey>(() => {
  const fromProps = props.tab
  const fromRoute = typeof route.params.tab === 'string' ? route.params.tab : ''
  const value = fromProps ?? fromRoute
  return value === 'heroes'
    || value === 'locations'
    || value === 'items'
    || value === 'retellings'
    || value === 'members'
    ? value
    : 'characters'
})

const objectClasses = computed<ObjectClass[] | null>(() =>
  tab.value === 'characters'
    || tab.value === 'heroes'
    || tab.value === 'locations'
    || tab.value === 'items'
    ? TAB_CLASSES[tab.value]
    : null,
)

// Строка поиска внутри вкладки — своя для каждой вкладки.
watch(
  tab,
  async () => {
    query.value = ''
    if (objectClasses.value) await campaign.loadTabObjects(objectClasses.value)
  },
  { immediate: true },
)

/** Список вкладки читается из IndexedDB с фильтром классов — работает офлайн. */
const objects = computed(() => campaign.tabObjects)

/** Живой фильтр: имя и описание, у персонажа ещё раса и краткое описание. */
const filteredObjects = computed(() =>
  objects.value.filter((object) => objectMatchesQuery(object, query.value)),
)

const filteredRetellings = computed(() =>
  campaign.retellingItems.filter((item) => matchesText([item.title, item.body], query.value)),
)

const sectionTitle = computed(() => {
  if (tab.value === 'retellings') return 'Истории'
  if (tab.value === 'members') return 'Участники'
  return TAB_TITLES[tab.value]
})

/** Классы, которые пользователь может создать на этой вкладке (героя — только владелец). */
const createClasses = computed<ObjectClass[]>(() =>
  objectClasses.value ? objectClasses.value.filter((cls) => campaign.canCreate(cls)) : [],
)

/** Полоска слева: тип виден сразу, ещё до чтения бейджа. */
function typeCardClass(object: LocalObject): string {
  if (object.objectType === 'HERO') return 'card--type-hero'
  if (object.objectType === 'CHARACTER') return 'card--type-npc'
  if (object.objectType === 'LOCATION') return 'card--type-location'
  return 'card--type-item'
}

function badgeClass(object: LocalObject): string {
  if (object.objectType === 'HERO') return 'badge--hero'
  if (object.objectType === 'CHARACTER') return 'badge--npc'
  if (object.objectType === 'LOCATION') return 'badge--location'
  return 'badge--item'
}

/** Короткая строка под названием: у персонажа — краткое описание, у героя — раса и класс. */
function excerpt(object: LocalObject): string {
  if (object.objectType === 'CHARACTER') return object.shortDescription ?? ''
  if (object.objectType === 'HERO') {
    return [object.race, object.klass].filter((value): value is string => Boolean(value)).join(' · ')
  }
  return ''
}

/** Пол есть только у героя и персонажа: в карточке показываем символ. */
function genderOf(object: LocalObject): string {
  return object.objectType === 'HERO' || object.objectType === 'CHARACTER'
    ? genderSymbol(object.gender)
    : ''
}

/** Ненавязчивый текст подгрузки: сколько объектов вкладки уже пришло с сервера. */
const tabProgressLabel = computed(() =>
  campaign.tabTotal > 0
    ? `Подгружаем: ${campaign.tabLoaded} из ${campaign.tabTotal}`
    : 'Подгружаем…',
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
          :debounce-ms="200"
        />
        <RouterLink
          v-for="cls in createClasses"
          :key="cls"
          class="btn btn--primary btn--sm"
          :to="{ name: 'entity-new', params: { campaignId: campaign.campaignId }, query: { class: cls } }"
        >
          + {{ OBJECT_CLASS_LABELS[cls] }}
        </RouterLink>
        <RouterLink
          v-if="tab === 'retellings' && campaign.canWriteRetellings"
          class="btn btn--primary btn--sm"
          :to="{ name: 'retelling-new', params: { campaignId: campaign.campaignId } }"
        >
          + История
        </RouterLink>
      </div>
    </div>

    <!-- Объекты вкладки: персонажи (герои + НПС), локации, предметы -->
    <template v-if="objectClasses">
      <div class="row row--between search-summary">
        <div class="row">
          <span class="faint">Найдено: {{ filteredObjects.length }}</span>
          <!-- Подгрузка не блокирует список: локальные данные уже показаны -->
          <span v-if="campaign.tabLoading" class="faint tab-progress" role="status">
            <span class="spinner spinner--sm" aria-hidden="true" />
            {{ tabProgressLabel }}
          </span>
        </div>
        <button
          v-if="query !== ''"
          class="btn btn--ghost btn--sm"
          type="button"
          @click="query = ''"
        >
          Очистить
        </button>
      </div>

      <EmptyState
        v-if="objects.length === 0"
        :title="`Пока нет: ${sectionTitle.toLowerCase()}`"
        :hint="
          createClasses.length > 0
            ? 'Создайте первую запись — она сохранится на устройстве и уйдёт на сервер при синхронизации.'
            : 'У вас нет прав на создание объектов этого типа.'
        "
      />
      <EmptyState
        v-else-if="filteredObjects.length === 0"
        title="Ничего не найдено"
        hint="Попробуйте изменить запрос — поиск идёт по локальной базе и работает без сети."
      />
      <div v-else class="grid">
        <RouterLink
          v-for="object in filteredObjects"
          :key="object.id"
          class="card card--link"
          :class="typeCardClass(object)"
          :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: object.id } }"
        >
          <span v-if="genderOf(object)" class="card__gender" title="Пол">
            {{ genderOf(object) }}
          </span>
          <div class="row card__head">
            <EntityAvatar :object="object" :size="40" />
            <h3 class="card__title">
              {{ object.name }}
              <span v-if="object.dirty === 1" class="dirty-dot" title="Есть неотправленная правка" />
            </h3>
          </div>
          <p v-if="excerpt(object)" class="card__excerpt">{{ excerpt(object) }}</p>
          <div class="card__meta">
            <span class="badge" :class="badgeClass(object)">
              {{ OBJECT_CLASS_LABELS[object.objectType] }}
            </span>
            <span v-if="object.conflict" class="badge badge--danger">расхождение</span>
            <span>{{ campaign.changeSummary(object) }}</span>
          </div>
        </RouterLink>
      </div>
    </template>

    <!-- Истории: черновики и публикации -->
    <template v-else-if="tab === 'retellings'">
      <div class="row row--between search-summary">
        <span class="faint">Найдено: {{ filteredRetellings.length }}</span>
        <button v-if="query !== ''" class="btn btn--ghost btn--sm" type="button" @click="query = ''">
          Очистить
        </button>
      </div>

      <EmptyState
        v-if="campaign.retellingItems.length === 0"
        title="Историй пока нет"
        hint="Черновик хранится только на устройстве. На сервер история уходит после нажатия «Опубликовать»."
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

<style scoped>
.card__gender {
  position: absolute;
  top: 8px;
  right: 12px;
  color: var(--text-muted);
  font-size: 1.05rem;
  line-height: 1;
}
.card__head {
  align-items: center;
  gap: 0.6rem;
}
.card__head .card__title {
  margin: 0;
}
.search-summary {
  min-height: 28px;
}
</style>
