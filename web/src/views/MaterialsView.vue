<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import EmptyState from '@/components/EmptyState.vue'
import EntityImage from '@/components/EntityImage.vue'
import { useCampaignStore } from '@/stores/campaign'
import type { LocalMaterial } from '@/db/schema'

/**
 * Вкладка «Материалы»: карты, портреты, письма и документы кампании.
 * Список читается из IndexedDB, поэтому открывается и без сети; правки
 * попадают в очередь синхронизации так же, как у объектов.
 */
const route = useRoute()
const campaign = useCampaignStore()

const campaignId = computed(() => String(route.params.campaignId ?? ''))
const materials = computed<LocalMaterial[]>(() => campaign.materials)

/** Первая картинка из файлов материала: она идёт миниатюрой. */
function firstImage(material: LocalMaterial): string | null {
  const image = material.files.find((file) => (file.contentType ?? '').startsWith('image/'))
  return image?.id ?? material.imageFileId
}

function documentsCount(material: LocalMaterial): number {
  return material.files.filter((file) => !(file.contentType ?? '').startsWith('image/')).length
}

function excerpt(material: LocalMaterial): string {
  const text = material.description.replace(/\[\[([^\]]+)\]\]/g, '$1')
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}
</script>

<template>
  <section class="stack">
    <div class="row row--between">
      <h2>Материалы</h2>
      <RouterLink
        v-if="campaign.canWriteEntities"
        class="btn btn--primary"
        :to="{ name: 'material-new', params: { campaignId } }"
      >
        Добавить материал
      </RouterLink>
    </div>

    <EmptyState
      v-if="materials.length === 0"
      title="Материалов пока нет"
      hint="Добавьте карту, портрет или письмо — они сохранятся на устройстве и уедут на сервер, когда появится сеть."
    />

    <div v-else class="grid">
      <RouterLink
        v-for="material in materials"
        :key="material.id"
        class="card card--link card--type-item"
        :to="{ name: 'material-edit', params: { campaignId, materialId: material.id } }"
      >
        <EntityImage
          v-if="firstImage(material)"
          :file-id="firstImage(material)"
          :alt="material.name"
          :max-height="180"
        />
        <h3 class="card__title">
          {{ material.name || 'Без названия' }}
          <span v-if="material.dirty === 1" class="dirty-dot" title="Есть неотправленная правка" />
        </h3>
        <p v-if="excerpt(material)" class="card__excerpt">{{ excerpt(material) }}</p>
        <div class="card__meta">
          <span v-if="documentsCount(material) > 0" class="badge">
            документов: {{ documentsCount(material) }}
          </span>
          <span v-if="material.conflict" class="badge badge--danger">расхождение</span>
        </div>
      </RouterLink>
    </div>
  </section>
</template>
