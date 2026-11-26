<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
import ListCard from '@/components/ListCard.vue'
import ListSort from '@/components/ListSort.vue'
import TriStateField from '@/components/TriStateField.vue'
import MembersPanel from '@/components/MembersPanel.vue'
import SearchBox from '@/components/SearchBox.vue'
import { genderSymbol } from '@/types/gender'
import { matchesText, objectMatchesQuery } from '@/db/search'
import { cardsApi, type CardTemplateDto } from '@/api'
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
type SortField = 'name' | 'createdAt' | 'updatedAt'
const sortField = ref<SortField>('name')
const sortAsc = ref(true)
const sortOptions = [{ value: 'name', label: 'по названию' }, { value: 'createdAt', label: 'по дате добавления' }, { value: 'updatedAt', label: 'по дате изменения' }]
const aliveFilter = ref<boolean | null>(null)
const intactFilter = ref<boolean | null>(null)
const magicFilter = ref<boolean | null>(null)
const settingFilter = ref<boolean | null>(null)
const raceFilter = ref('all')
const classFilter = ref('all')
const typeFilter = ref('all')
const ownerFilter = ref('all')
const nameFilter = ref('')
const cardTemplates = ref<CardTemplateDto[]>([])
const matchedCards = ref<Record<string, string>>({})
const cardsReady = ref(false)
const CARDS_ENABLED = false
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
    sortField.value = 'name'
    sortAsc.value = true
    aliveFilter.value = null
    intactFilter.value = null
    magicFilter.value = null
    settingFilter.value = null
    raceFilter.value = 'all'
    classFilter.value = 'all'
    typeFilter.value = 'all'
    ownerFilter.value = 'all'
    nameFilter.value = ''
    if (objectClasses.value) await campaign.loadTabObjects(objectClasses.value)
  },
  { immediate: true },
)

/** Список вкладки читается из IndexedDB с фильтром классов — работает офлайн. */
const objects = computed(() => campaign.tabObjects)

/** Живой фильтр: имя и описание, у персонажа ещё раса и краткое описание. */
const typeOptions = computed(() => {
  const values = new Set<string>()
  for (const object of objects.value) {
    const value = object.objectType === 'ITEM' ? object.itemType : object.objectType === 'LOCATION' ? object.locationType : ''
    if (value) values.add(value)
  }
  return [...values].sort((a, b) => a.localeCompare(b, 'ru'))
})

const raceOptions = computed(() => {
  const values = new Set<string>()
  for (const object of objects.value) if (object.objectType === 'CHARACTER' && object.race) values.add(object.race)
  return [...values].sort((a, b) => a.localeCompare(b, 'ru'))
})

const classOptions = computed(() => {
  const values = new Set<string>()
  for (const object of objects.value) if (object.objectType === 'CHARACTER' && object.klass) values.add(object.klass)
  return [...values].sort((a, b) => a.localeCompare(b, 'ru'))
})

const heroOptions = computed(() =>
  campaign.objects
    .filter((object) => object.objectType === 'HERO' && !object.deleted)
    .sort((a, b) => a.name.localeCompare(b.name, 'ru')),
)

const filteredObjects = computed(() =>
  objects.value.filter((object) => {
    if (!objectMatchesQuery(object, query.value)) return false
    if (nameFilter.value.trim() !== '' && !object.name.toLowerCase().includes(nameFilter.value.trim().toLowerCase())) {
      return false
    }
    if (tab.value === 'characters') {
      if (aliveFilter.value !== null && object.alive !== aliveFilter.value) return false
      if (raceFilter.value !== 'all' && (object.race ?? '') !== raceFilter.value) return false
      if (classFilter.value !== 'all' && (object.klass ?? '') !== classFilter.value) return false
    }
    if (tab.value === 'locations' || tab.value === 'items') {
      if (intactFilter.value !== null && (object.intact !== false) !== intactFilter.value) return false
      if (typeFilter.value !== 'all') {
        const value = tab.value === 'items' ? object.itemType : object.locationType
        if ((value ?? '') !== typeFilter.value) return false
      }
      if (tab.value === 'items') {
        if (magicFilter.value !== null && (object.magic === true) !== magicFilter.value) return false
        if (settingFilter.value !== null && (object.hasSetting === true) !== settingFilter.value) return false
        if (ownerFilter.value !== 'all' && (object.ownerHeroId ?? '') !== ownerFilter.value) return false
      }
    }
    return true
  }),
)

const visibleObjects = computed(() => {
  const list = [...filteredObjects.value]
  const direction = sortAsc.value ? 1 : -1
  list.sort((a, b) => {
    if (sortField.value === 'name') return a.name.localeCompare(b.name, 'ru') * direction
    const left = sortField.value === 'createdAt' ? a.createdAt : a.updatedAt
    const right = sortField.value === 'createdAt' ? b.createdAt : b.updatedAt
    return left.localeCompare(right) * direction
  })
  return list
})

function resetFilters(): void {
  aliveFilter.value = null
  intactFilter.value = null
  magicFilter.value = null
  settingFilter.value = null
  raceFilter.value = 'all'
  classFilter.value = 'all'
  typeFilter.value = 'all'
  ownerFilter.value = 'all'
  nameFilter.value = ''
}

function toggleSortDirection(): void {
  sortAsc.value = !sortAsc.value
}

const filteredRetellings = computed(() =>
  campaign.retellingItems.filter((item) => matchesText([item.title, item.body], query.value)),
)

const visibleRetellings = computed(() => {
  const list = [...filteredRetellings.value]
  const direction = sortAsc.value ? 1 : -1
  list.sort((a, b) => {
    if (sortField.value === 'name') return a.title.localeCompare(b.title, 'ru') * direction
    const left = sortField.value === 'createdAt' ? (a.createdAt ?? '') : (a.updatedAt ?? '')
    const right = sortField.value === 'createdAt' ? (b.createdAt ?? '') : (b.updatedAt ?? '')
    return left.localeCompare(right) * direction
  })
  return list
})

const sectionTitle = computed(() => {
  if (tab.value === 'retellings') return 'Истории'
  if (tab.value === 'members') return 'Участники'
  return TAB_TITLES[tab.value]
})

/** Классы, которые пользователь может создать на этой вкладке (героя — только владелец). */
const createClasses = computed<ObjectClass[]>(() =>
  objectClasses.value ? objectClasses.value.filter((cls) => campaign.canCreate(cls)) : [],
)

function objectFields(object: LocalObject): Record<string, unknown> {
  const row = object as unknown as Record<string, unknown>
  return {
    name: row.name, objectType: row.objectType, type: row.objectType,
    race: row.race, klass: row.klass, gender: row.gender, alive: row.alive,
    shortDescription: row.shortDescription, itemType: row.itemType,
    locationType: row.locationType, magic: row.magic,
    hasSetting: row.hasSetting, ownerName: row.ownerName,
  }
}

function templateFor(object: LocalObject): CardTemplateDto | null {
  const id = matchedCards.value[object.id]
  return id ? cardTemplates.value.find((item) => item.id === id) ?? null : null
}

async function loadCards(): Promise<void> {
  if (!CARDS_ENABLED) {
    cardTemplates.value = []
    matchedCards.value = {}
    cardsReady.value = true
    return
  }
  if (!campaign.campaignId) return
  cardsReady.value = false
  try {
    const templates = await cardsApi.forCampaign(campaign.campaignId)
    cardTemplates.value = templates
    if (templates.length === 0) {
      matchedCards.value = {}
      return
    }
    const objects = filteredObjects.value
    if (objects.length === 0) {
      matchedCards.value = {}
      return
    }
    const ids = await cardsApi.match(
      campaign.campaignId,
      objects.map((object) => ({ objectType: object.objectType, kind: 'LIST', fields: objectFields(object) })),
    )
    const next: Record<string, string> = {}
    objects.forEach((object, index) => {
      if (ids[index]) next[object.id] = ids[index] as string
    })
    matchedCards.value = next
  } catch {
    cardTemplates.value = []
    matchedCards.value = {}
  } finally {
    cardsReady.value = true
  }
}

watch(filteredObjects, () => { void loadCards() })
onMounted(() => { void loadCards() })

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

    <div v-if="tab !== 'members'" class="row" style="justify-content: flex-end">
      <ListSort v-model="sortField" v-model:ascending="sortAsc" :options="sortOptions" />
    </div>

    <!-- Объекты вкладки: персонажи (герои + НПС), локации, предметы -->
    <template v-if="objectClasses">
      <div class="tab-layout" :class="{ 'tab-layout--full': tab === 'heroes' }">
        <aside v-if="tab === 'characters' || tab === 'locations' || tab === 'items'" class="filters-panel">
          <h3>Фильтр</h3>
          <label class="field__label" for="filter-name">Имя</label>
          <input id="filter-name" v-model="nameFilter" class="input" type="search" placeholder="часть имени" />
          <template v-if="tab === 'characters'">
            <TriStateField
              label="Статус"
              :model-value="aliveFilter"
              true-label="жив"
              false-label="мёртв"
              null-label="всё"
              @update:model-value="aliveFilter = $event"
            />
            <label class="field__label" for="filter-race">Раса</label>
            <select id="filter-race" v-model="raceFilter" class="select">
              <option value="all">все расы</option>
              <option v-for="race in raceOptions" :key="race" :value="race">{{ race }}</option>
            </select>
            <label class="field__label" for="filter-class">Класс</label>
            <select id="filter-class" v-model="classFilter" class="select">
              <option value="all">все классы</option>
              <option v-for="klass in classOptions" :key="klass" :value="klass">{{ klass }}</option>
            </select>
          </template>
          <template v-if="tab === 'locations' || tab === 'items'">
            <TriStateField
              label="Состояние"
              :model-value="intactFilter"
              true-label="целое"
              false-label="разрушено"
              null-label="всё"
              @update:model-value="intactFilter = $event"
            />
            <label class="field__label" for="filter-type">Тип</label>
            <select id="filter-type" v-model="typeFilter" class="select">
              <option value="all">все типы</option>
              <option v-for="type in typeOptions" :key="type" :value="type">{{ type }}</option>
            </select>
          </template>
          <template v-if="tab === 'items'">
            <TriStateField
              label="Магический"
              :model-value="magicFilter"
              true-label="магический"
              false-label="не магический"
              null-label="всё"
              @update:model-value="magicFilter = $event"
            />
            <TriStateField
              label="Настройка"
              :model-value="settingFilter"
              true-label="настроен"
              false-label="не настроен"
              null-label="всё"
              @update:model-value="settingFilter = $event"
            />
            <label class="field__label" for="filter-owner">Владелец</label>
            <select id="filter-owner" v-model="ownerFilter" class="select">
              <option value="all">все владельцы</option>
              <option v-for="hero in heroOptions" :key="hero.id" :value="hero.id">{{ hero.name }}</option>
            </select>
          </template>
          <button class="btn btn--ghost btn--sm" type="button" @click="resetFilters">Сбросить</button>
        </aside>
        <div class="tab-main">
      <div class="row row--between search-summary">
        <div class="row">
          <span class="faint">Найдено: {{ filteredObjects.length }}</span>
          <!-- Подгрузка не блокирует список: локальные данные уже показаны -->
          <span v-if="campaign.tabLoading" class="faint tab-progress" role="status">
            <span class="spinner spinner--sm" aria-hidden="true" />
            {{ tabProgressLabel }}
          </span>
        </div>
        <div class="row">
          <button v-if="query !== ''" class="btn btn--ghost btn--sm" type="button" @click="query = ''">Очистить</button>
        </div>
      </div>

      <div v-if="!cardsReady" class="loading"><span class="spinner" /> Готовим карточки…</div>
      <EmptyState
        v-else-if="objects.length === 0"
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
          v-for="object in visibleObjects"
          :key="object.id"
          class="card card--link"
          :class="typeCardClass(object)"
          :to="{ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: object.id } }"
        >
          <ListCard v-if="templateFor(object)" :object="object" :template="templateFor(object)!" />
          <template v-else>
          <span v-if="genderOf(object)" class="card__gender" title="Пол">
            {{ genderOf(object) }}
          </span>
          <div class="row card__head">
            <EntityAvatar :object="object" :size="40" />
            <h3 class="card__title">
              <span
                v-if="object.objectType === 'HERO' || object.objectType === 'CHARACTER' || object.objectType === 'ITEM' || object.objectType === 'LOCATION'"
                class="life-dot"
                :class="object.objectType === 'CHARACTER' || object.objectType === 'HERO' ? (object.alive === true ? 'life-dot--alive' : object.alive === false ? 'life-dot--dead' : 'life-dot--unknown') : ((object as any).intact === false ? 'life-dot--dead' : 'life-dot--alive')"
                :title="object.objectType === 'CHARACTER' || object.objectType === 'HERO' ? (object.alive === true ? 'жив' : object.alive === false ? 'мёртв' : 'неизвестно') : ((object as any).intact === false ? 'разрушено' : 'целое')"
              />
              
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
            <span v-if="object.objectType === 'HERO' && object.color" class="hero-color-square" :style="{ background: object.color }" :title="'Цвет героя: ' + object.color" />
          </div>
          </template>
        </RouterLink>
      </div>
        </div>
      </div>
    </template>

    <!-- Истории: черновики и публикации -->
    <template v-else-if="tab === 'retellings'">
      <div class="row row--between search-summary">
        <span class="faint">Найдено: {{ filteredRetellings.length }}</span>
        <div class="row">
          <button v-if="query !== ''" class="btn btn--ghost btn--sm" type="button" @click="query = ''">Очистить</button>
        </div>
      </div>

      <EmptyState
        v-if="campaign.retellingItems.length === 0"
        title="Историй пока нет"
        hint="Черновик хранится только на устройстве. На сервер история уходит после нажатия «Опубликовать»."
      />
      <EmptyState v-else-if="filteredRetellings.length === 0" title="Ничего не найдено" />
      <div v-else class="list">
        <RouterLink
          v-for="item in visibleRetellings"
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
.hero-color-square { display: inline-block; width: 16px; height: 16px; border-radius: 4px; margin-left: auto; border: 1px solid var(--border); }
.tab-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 1.25rem; align-items: start; }
.filters-panel { position: sticky; top: 1rem; display: grid; gap: .45rem; padding: 1rem; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
.filters-panel h3 { margin: 0 0 .35rem; font-size: 1rem; }
.tab-main { min-width: 0; }
.tab-layout--full { grid-template-columns: minmax(0, 1fr); }
@media (max-width: 900px) { .tab-layout { grid-template-columns: 1fr; } .filters-panel { position: static; } }
.search-summary {
  min-height: 28px;
}
.life-dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.life-dot--alive { background: #2e7d32; box-shadow: 0 0 0 2px rgba(46,125,50,.22); }
.life-dot--dead { background: #c62828; box-shadow: 0 0 0 2px rgba(198,40,40,.22); }
.life-dot--unknown { background: #8a8a8a; box-shadow: 0 0 0 2px rgba(138,138,138,.22); }
</style>
