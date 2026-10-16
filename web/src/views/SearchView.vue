<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { searchApi, type SearchResultItem } from '@/api'
import { OBJECT_CLASSES, SEARCH_KIND_LABELS, SEARCH_KINDS, type SearchKind } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import MarkupText from '@/components/MarkupText.vue'
import SearchBox from '@/components/SearchBox.vue'
import { searchLocal, type LocalSearchItem } from '@/db/search'
import { useSyncStore } from '@/stores/sync'
import { useUiStore } from '@/stores/ui'
import { formatRelative } from '@/utils/datetime'

const route = useRoute()
const sync = useSyncStore()
const ui = useUiStore()

const query = ref('')
/** Фильтр по видам (`api.md` §8): четыре класса объектов и пересказы. */
const selectedKinds = ref<SearchKind[]>([...SEARCH_KINDS])
const localResults = ref<LocalSearchItem[]>([])
const localTotal = ref(0)
const serverResults = ref<SearchResultItem[]>([])
const searching = ref(false)

const campaignId = computed(() => String(route.params.campaignId ?? ''))

/** Виды, которые уходят в локальный поиск (`classes`) и на сервер (`kinds`). */
const selectedClasses = computed(() => OBJECT_CLASSES.filter((cls) => selectedKinds.value.includes(cls)))
const includeRetellings = computed(() => selectedKinds.value.includes('RETELLING'))

/** Результаты сервера, которых ещё нет на устройстве. */
const serverOnly = computed(() => {
  const localIds = new Set(localResults.value.map((item) => item.id))
  return serverResults.value.filter((item) => !localIds.has(item.id))
})

async function run(): Promise<void> {
  const q = query.value.trim()
  if (q === '') {
    localResults.value = []
    localTotal.value = 0
    serverResults.value = []
    return
  }
  searching.value = true
  try {
    const local = await searchLocal(campaignId.value, q, {
      classes: selectedClasses.value,
      includeRetellings: includeRetellings.value,
      limit: 100,
    })
    localResults.value = local.items
    localTotal.value = local.total

    if (ui.online && selectedKinds.value.length > 0) {
      try {
        const response = await searchApi.search(campaignId.value, q, selectedKinds.value, 50)
        serverResults.value = response.items
      } catch {
        serverResults.value = []
      }
    } else {
      serverResults.value = []
    }
  } finally {
    searching.value = false
  }
}

watch([query, selectedKinds], () => void run(), { deep: true })

onMounted(() => {
  void run()
})

function toggleKind(kind: SearchKind): void {
  selectedKinds.value = selectedKinds.value.includes(kind)
    ? selectedKinds.value.filter((item) => item !== kind)
    : [...selectedKinds.value, kind]
}

function kindLabel(kind: SearchKind): string {
  return SEARCH_KIND_LABELS[kind]
}

async function syncNow(): Promise<void> {
  await sync.syncNow()
  await run()
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Поиск по кампании</h1>
        <div class="page-header__subtitle">
          Ищем по локальной базе — работает без сети. При наличии связи результаты дополняются сервером.
        </div>
      </div>
      <button class="btn btn--sm" type="button" :disabled="sync.busy" @click="syncNow">
        Обновить с сервера
      </button>
    </div>

    <SearchBox v-model="query" placeholder="Имя, описание, раса, текст пересказа…" />

    <div class="row">
      <span class="faint">Искать среди:</span>
      <label v-for="kind in SEARCH_KINDS" :key="kind" class="chip" style="cursor: pointer">
        <input
          type="checkbox"
          :checked="selectedKinds.includes(kind)"
          @change="toggleKind(kind)"
        />
        {{ SEARCH_KIND_LABELS[kind] }}
      </label>
    </div>

    <div v-if="searching" class="loading"><span class="spinner" aria-hidden="true" /> Ищем…</div>

    <template v-else>
      <EmptyState
        v-if="query.trim() === ''"
        title="Введите запрос"
        hint="Например: «Лира», «подземелье», «полуэльф»."
      />
      <template v-else>
        <div class="row row--between">
          <h2 style="margin: 0">Найдено на устройстве: {{ localTotal }}</h2>
          <span v-if="!ui.online" class="badge badge--warning">офлайн</span>
        </div>

        <EmptyState
          v-if="localResults.length === 0"
          title="На устройстве ничего не найдено"
          :hint="
            ui.online && serverOnly.length > 0
              ? 'Похожие записи есть на сервере — синхронизируйте кампанию.'
              : 'Попробуйте другой запрос.'
          "
        />
        <div v-else class="list">
          <RouterLink
            v-for="item in localResults"
            :key="`${item.kind}-${item.id}`"
            class="list-row"
            :to="
              item.kind === 'RETELLING'
                ? { name: 'retelling-detail', params: { campaignId, retellingId: item.id } }
                : { name: 'entity-detail', params: { campaignId, entityId: item.id } }
            "
          >
            <div class="list-row__main">
              <div class="list-row__title">
                {{ item.title }}
                <span class="badge">{{ kindLabel(item.kind) }}</span>
                <span v-if="item.isDraft" class="badge badge--warning">черновик</span>
              </div>
              <MarkupText :text="item.snippet" snippet />
              <div class="faint">{{ formatRelative(item.updatedAt) }}</div>
            </div>
          </RouterLink>
        </div>

        <template v-if="serverOnly.length > 0">
          <h2>Есть на сервере, но не загружено на устройство: {{ serverOnly.length }}</h2>
          <div class="list">
            <div v-for="item in serverOnly" :key="`server-${item.kind}-${item.id}`" class="list-row">
              <div class="list-row__main">
                <div class="list-row__title">
                  {{ item.title }}
                  <span class="badge badge--info">{{ kindLabel(item.kind) }}</span>
                </div>
                <MarkupText :text="item.snippet" snippet />
                <div class="faint">ревизия {{ item.revision }} · {{ formatRelative(item.updatedAt) }}</div>
              </div>
            </div>
          </div>
          <button class="btn" type="button" :disabled="sync.busy" @click="syncNow">
            Синхронизировать и открыть локально
          </button>
        </template>
      </template>
    </template>
  </div>
</template>
