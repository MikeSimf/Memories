<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { errorMessage, materialsApi, type MaterialDto } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import ListSort from '@/components/ListSort.vue'
import EntityImage from '@/components/EntityImage.vue'
import { useCampaignStore } from '@/stores/campaign'

/**
 * Вкладка «Материалы»: карты, портреты, письма и документы кампании.
 *
 * <p>В отличие от объектов справочника, материалы на сайте работают только
 * онлайн: список и правки идут напрямую в API, без локальной копии.
 */
const route = useRoute()
const campaign = useCampaignStore()

const campaignId = computed(() => String(route.params.campaignId ?? ''))
const materials = ref<MaterialDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const sortField = ref<'name' | 'createdAt' | 'updatedAt'>('name')
const sortAsc = ref(true)
const sortOptions = [
  { value: 'name', label: 'по названию' },
  { value: 'createdAt', label: 'по дате добавления' },
  { value: 'updatedAt', label: 'по дате изменения' },
]
const sortedMaterials = computed(() => {
  const list = [...materials.value]
  const direction = sortAsc.value ? 1 : -1
  list.sort((a, b) => {
    if (sortField.value === 'name') return a.name.localeCompare(b.name, 'ru') * direction
    const left = sortField.value === 'createdAt' ? (a.createdAt ?? '') : (a.updatedAt ?? '')
    const right = sortField.value === 'createdAt' ? (b.createdAt ?? '') : (b.updatedAt ?? '')
    return left.localeCompare(right) * direction
  })
  return list
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await materialsApi.list(campaignId.value)
    materials.value = page.items ?? []
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** Первая картинка из файлов материала: она идёт миниатюрой. */
function firstImage(material: MaterialDto): string | null {
  const image = (material.files ?? []).find((file) => (file.contentType ?? '').startsWith('image/'))
  return image?.id ?? material.imageFileId
}

function documentsCount(material: MaterialDto): number {
  return (material.files ?? []).filter((file) => !(file.contentType ?? '').startsWith('image/')).length
}

function excerpt(material: MaterialDto): string {
  const text = (material.description ?? '').replace(/\[\[([^\]]+)\]\]/g, '$1')
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}
</script>

<template>
  <section class="stack">
    <div class="row row--between">
      <h2>Материалы</h2>
      <ListSort v-model="sortField" v-model:ascending="sortAsc" :options="sortOptions" />
      <div class="row">
        <button class="btn btn--ghost" type="button" :disabled="loading" @click="load">
          Обновить
        </button>
        <RouterLink
          v-if="campaign.canWriteEntities"
          class="btn btn--primary"
          :to="{ name: 'material-new', params: { campaignId } }"
        >
          Добавить материал
        </RouterLink>
      </div>
    </div>

    <div v-if="error" class="notice notice--danger">
      {{ error }}
      <button class="btn btn--ghost btn--sm" type="button" @click="load">Повторить</button>
    </div>

    <p v-if="loading" class="faint">Загружаю материалы…</p>

    <EmptyState
      v-else-if="!error && materials.length === 0"
      title="Материалов пока нет"
      hint="Добавьте карту, портрет или письмо — они хранятся на сервере и доступны с любого устройства."
    />

    <div v-else-if="materials.length > 0" class="grid">
      <RouterLink
        v-for="material in sortedMaterials"
        :key="material.id"
        class="card card--link card--type-item"
        :to="{ name: 'material-detail', params: { campaignId, materialId: material.id } }"
      >
        <EntityImage
          v-if="firstImage(material)"
          :file-id="firstImage(material)"
          :alt="material.name"
          :max-height="180"
        />
        <h3 class="card__title">{{ material.name || 'Без названия' }}</h3>
        <p v-if="excerpt(material)" class="card__excerpt">{{ excerpt(material) }}</p>
        <div class="card__meta">
          <span v-if="documentsCount(material) > 0" class="badge">
            документов: {{ documentsCount(material) }}
          </span>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
