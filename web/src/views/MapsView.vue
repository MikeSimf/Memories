<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage, mapsApi, type InteractiveMapDto } from '@/api'
import EntityImage from '@/components/EntityImage.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useCampaignStore } from '@/stores/campaign'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const maps = ref<InteractiveMapDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const campaignId = String(route.params.campaignId ?? '')

async function load(): Promise<void> {
  loading.value = true
  try {
    maps.value = await mapsApi.list(campaignId)
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  void router.push({ name: 'map-new', params: { campaignId } })
}

async function remove(map: InteractiveMapDto): Promise<void> {
  if (!confirm(`Удалить карту «${map.name}»?`)) return
  try {
    await mapsApi.remove(campaignId, map.id)
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="row row--between">
      <h2>Интерактивные карты</h2>
      <button v-if="campaign.canCreate('LOCATION')" class="btn btn--primary" type="button" @click="openCreate">
        + Карта
      </button>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем карты…</div>
    <EmptyState
      v-else-if="maps.length === 0"
      title="Пока нет карт"
      hint="Создайте карту, загрузите фон и расставьте метки персонажей, локаций и предметов."
    />
    <div v-else class="maps-grid">
      <article v-for="map in maps" :key="map.id" class="map-card">
        <RouterLink :to="{ name: 'map-detail', params: { campaignId, mapId: map.id } }" class="map-card__image">
          <EntityImage v-if="map.imageFileId" :file-id="map.imageFileId" alt="" />
          <span v-else class="map-card__placeholder">Карта</span>
        </RouterLink>
        <div class="map-card__body">
          <RouterLink :to="{ name: 'map-detail', params: { campaignId, mapId: map.id } }">
            <strong>{{ map.name }}</strong>
          </RouterLink>
          <p class="faint">{{ map.description || 'Без описания' }}</p>
          <span class="faint">Меток: {{ map.markerCount }}</span>
          <div v-if="campaign.canCreate('LOCATION')" class="row">
            <RouterLink class="btn btn--sm" :to="{ name: 'map-edit', params: { campaignId, mapId: map.id } }">Изменить</RouterLink>
            <button class="btn btn--danger btn--sm" type="button" @click="remove(map)">Удалить</button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.maps-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.map-card { border: 1px solid var(--border); border-radius: 12px; overflow: hidden; background: var(--bg-elev); }
.map-card__image { display: block; height: 150px; background: var(--bg-elev-2, rgba(127,127,127,.12)); }
.map-card__image img { width: 100%; height: 100%; object-fit: cover; }
.map-card__placeholder { display: grid; place-items: center; height: 100%; color: var(--text-muted); }
.map-card__body { padding: 10px 12px; display: flex; flex-direction: column; gap: 6px; }
</style>
