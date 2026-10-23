<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { errorMessage, materialsApi, type MaterialDto } from '@/api'
import EntityImage from '@/components/EntityImage.vue'
import MarkupText from '@/components/MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const campaign = useCampaignStore()
const campaignId = computed(() => String(route.params.campaignId ?? ''))
const materialId = computed(() => String(route.params.materialId ?? ''))
const material = ref<MaterialDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const canEdit = computed(() => campaign.canCreate('ITEM'))

const imageId = computed(() => {
  const file = (material.value?.files ?? []).find((item) => (item.contentType ?? '').startsWith('image/'))
  return file?.id ?? material.value?.avatarFileId ?? material.value?.imageFileId ?? null
})

const links = computed(() =>
  (material.value?.links ?? [])
    .map((link) => (link.targetId ? campaign.objectById(link.targetId) : null))
    .filter((object) => object !== null),
)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    material.value = await materialsApi.get(campaignId.value, materialId.value)
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(materialId, load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>{{ material?.name || 'Материал' }}</h1>
        <div class="page-header__subtitle"><span class="badge">Материал</span></div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'campaign-materials', params: { campaignId } }">
        К материалам
      </RouterLink>
      <RouterLink
        v-if="canEdit && material"
        class="btn btn--sm"
        :to="{ name: 'material-edit', params: { campaignId, materialId } }"
      >
        Редактировать
      </RouterLink>
    </div>

    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем…</div>
    <p v-else-if="error" class="notice notice--danger">{{ error }}</p>
    <template v-else-if="material">
      <EntityImage v-if="imageId" :file-id="imageId" :alt="material.name" />
      <div class="card">
        <MarkupText v-if="material.description" :text="material.description" />
        <p v-else class="faint">Не заполнено</p>
      </div>

      <div v-if="links.length > 0" class="card">
        <h2>Ссылки</h2>
        <div class="mention-list">
          <RouterLink
            v-for="link in links"
            :key="link.id"
            class="mention-row"
            :to="{ name: 'entity-detail', params: { campaignId, entityId: link.id } }"
          >
            <span class="mention-row__name">{{ link.name }}</span>
          </RouterLink>
        </div>
      </div>

      <div v-if="material.files?.length" class="card stack stack--sm">
        <h2>Файлы</h2>
        <a v-for="file in material.files" :key="file.id" :href="`/api/files/${file.id}`" target="_blank">
          {{ file.name || 'Файл' }}
        </a>
      </div>

      <div class="faint">
        Создан(а) {{ material.createdBy ? campaign.userName(material.createdBy) : '—' }} ·
        изменён(а) {{ material.updatedBy ? campaign.userName(material.updatedBy) : '—' }} ·
        {{ formatDateTime(material.updatedAt) }}
      </div>
    </template>
  </div>
</template>

<style scoped>
.mention-list { display: flex; flex-direction: column; gap: 6px; }
.mention-row { display: flex; align-items: center; color: inherit; text-decoration: none; }
.mention-row__name { font-weight: 600; }
</style>
