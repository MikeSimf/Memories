<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { errorMessage, mapsApi, type InteractiveMapDetailDto, type MapMarkerDto } from '@/api'
import EntityImage from '@/components/EntityImage.vue'
import { useCampaignStore } from '@/stores/campaign'
import { typeIconPath } from '@/ui/typeIcons'

const route = useRoute()
const campaign = useCampaignStore()
const campaignId = String(route.params.campaignId ?? '')
const mapId = String(route.params.mapId ?? '')
const map = ref<InteractiveMapDetailDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, tx: 0, ty: 0 })
const adding = ref(false)
const pick = ref<{ x: number; y: number } | null>(null)
const chosenEntityId = ref('')
const selectedMarker = ref<MapMarkerDto | null>(null)

const canEdit = computed(() => campaign.canCreate('LOCATION'))

const markerGroups = computed(() => {
  if (!map.value) return []
  const cell = scale.value >= 1.8 ? 0.001 : 0.06
  const groups = new Map<string, MapMarkerDto[]>()
  for (const marker of map.value.markers) {
    const key = `${Math.round(marker.x / cell)}:${Math.round(marker.y / cell)}`
    const list = groups.get(key) ?? []
    list.push(marker)
    groups.set(key, list)
  }
  return [...groups.values()].map((markers) => ({
    x: markers.reduce((sum, item) => sum + item.x, 0) / markers.length,
    y: markers.reduce((sum, item) => sum + item.y, 0) / markers.length,
    markers,
  }))
})
const available = computed(() => {
  const used = new Set((map.value?.markers ?? []).map((marker) => marker.entityId))
  return campaign.objects.filter((object) => !object.deleted && !used.has(object.id))
})

async function load(): Promise<void> {
  loading.value = true
  try {
    map.value = await mapsApi.get(campaignId, mapId)
    await campaign.loadTabObjects(['HERO', 'CHARACTER', 'LOCATION', 'ITEM'])
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function zoom(delta: number): void {
  scale.value = Math.max(0.6, Math.min(4, scale.value + delta))
}

function wheel(event: WheelEvent): void {
  event.preventDefault()
  zoom(event.deltaY < 0 ? 0.15 : -0.15)
}

function startDrag(event: PointerEvent): void {
  if ((event.target as HTMLElement).closest('.map-marker')) return
  dragging.value = true
  dragStart.value = { x: event.clientX, y: event.clientY, tx: tx.value, ty: ty.value }
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
}

function moveDrag(event: PointerEvent): void {
  if (!dragging.value) return
  tx.value = dragStart.value.tx + event.clientX - dragStart.value.x
  ty.value = dragStart.value.ty + event.clientY - dragStart.value.y
}

function endDrag(): void {
  dragging.value = false
}

function mapClick(event: MouseEvent): void {
  if (!adding.value || !map.value) return
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  pick.value = {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
  }
  chosenEntityId.value = ''
}

async function addMarker(): Promise<void> {
  if (!pick.value || !chosenEntityId.value) return
  try {
    await mapsApi.addMarker(campaignId, mapId, chosenEntityId.value, pick.value.x, pick.value.y)
    pick.value = null
    adding.value = false
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function deleteMarker(): Promise<void> {
  if (!selectedMarker.value) return
  try {
    await mapsApi.removeMarker(campaignId, mapId, selectedMarker.value.id)
    selectedMarker.value = null
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

function openGroup(group: { markers: MapMarkerDto[] }): void {
  if (group.markers.length === 1) {
    selectedMarker.value = group.markers[0]
    return
  }
  scale.value = Math.min(4, scale.value * 1.6)
}

function markerIcon(marker: MapMarkerDto): string {
  if (marker.objectType === 'HERO' || marker.objectType === 'CHARACTER') {
    return 'M12 4.4a3.4 3.4 0 1 0 0 6.8a3.4 3.4 0 1 0 0-6.8M5 20.4c0-3.4 3.1-6 7-6s7 2.6 7 6'
  }
  if (marker.objectType === 'LOCATION') return typeIconPath('LOCATION', '')
  return typeIconPath('ITEM', '')
}

function markerClass(marker: MapMarkerDto): string {
  return `map-marker--${marker.objectType.toLowerCase()}`
}

function objectTypeLabel(type: string): string {
  return { HERO: 'Герой', CHARACTER: 'Персонаж', LOCATION: 'Локация', ITEM: 'Предмет' }[type] ?? type
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>{{ map?.name ?? 'Карта' }}</h1>
        <div class="page-header__subtitle">{{ map?.description }}</div>
      </div>
      <RouterLink v-if="canEdit && map" class="btn btn--sm" :to="{ name: 'map-edit', params: { campaignId, mapId } }">Изменить</RouterLink>
      <RouterLink class="btn btn--sm" :to="{ name: 'campaign-maps', params: { campaignId } }">К картам</RouterLink>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем карту…</div>
    <template v-else-if="map">
      <div class="row">
        <button class="btn btn--sm" type="button" @click="zoom(0.25)">+</button>
        <button class="btn btn--sm" type="button" @click="zoom(-0.25)">−</button>
        <button class="btn btn--sm" type="button" @click="scale = 1; tx = 0; ty = 0">Сбросить</button>
        <button v-if="canEdit" class="btn btn--sm" :class="{ 'btn--primary': adding }" type="button" @click="adding = !adding">
          {{ adding ? 'Выберите точку на карте' : 'Добавить метку' }}
        </button>
      </div>

      <div
        class="map-viewport"
        @wheel="wheel"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
        @click="mapClick"
      >
        <div class="map-canvas" :style="{ transform: `translate(${tx}px, ${ty}px) scale(${scale})` }">
          <div v-if="map.imageFileId" class="map-image"><EntityImage :file-id="map.imageFileId" alt="" :interactive="false" :max-height="10000" /></div>
          <div v-else class="map-image map-image--empty">Фон не загружен</div>
          <button
            v-for="(group, index) in markerGroups"
            :key="index"
            class="map-marker"
            :class="[markerClass(group.markers[0]), { 'map-marker--cluster': group.markers.length > 1 }]"
            :style="{ left: `${group.x * 100}%`, top: `${group.y * 100}%` }"
            :title="group.markers.map((item) => item.name).join(', ')"
            type="button"
            @click.stop="openGroup(group)"
          >
            <template v-if="group.markers.length > 1">{{ group.markers.length }}</template>
            <svg v-else class="map-marker__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path :d="markerIcon(group.markers[0])" />
            </svg>
          </button>
        </div>
      </div>

      <div v-if="selectedMarker" class="map-modal" @click.self="selectedMarker = null">
        <div class="card stack map-modal__card">
          <strong>{{ selectedMarker.name }}</strong>
          <span class="faint">{{ objectTypeLabel(selectedMarker.objectType) }}</span>
          <div class="row">
            <RouterLink class="btn btn--sm" :to="{ name: 'entity-detail', params: { campaignId, entityId: selectedMarker.entityId } }">Открыть</RouterLink>
            <button v-if="canEdit" class="btn btn--danger btn--sm" type="button" @click="deleteMarker">Удалить метку</button>
            <button class="btn btn--sm" type="button" @click="selectedMarker = null">Закрыть</button>
          </div>
        </div>
      </div>

      <div v-if="pick" class="map-modal" @click.self="pick = null">
        <div class="card stack map-modal__card">
          <strong>Что находится в этой точке?</strong>
          <select v-model="chosenEntityId" class="select">
            <option value="" disabled>Выберите объект</option>
            <option v-for="object in available" :key="object.id" :value="object.id">
              {{ objectTypeLabel(object.objectType) }} · {{ object.name }}
            </option>
          </select>
          <div class="row">
            <button class="btn btn--primary" :disabled="!chosenEntityId" type="button" @click="addMarker">Поставить метку</button>
            <button class="btn" type="button" @click="pick = null">Отмена</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.map-viewport { height: auto; min-height: 0; overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: #1b1b1b; cursor: grab; touch-action: none; }
.map-canvas { position: relative; width: 100%; transform-origin: 0 0; }
.map-image { display: block; width: 100%; cursor: grab; }
.map-image :deep(img) { display: block; width: 100%; height: auto; cursor: inherit; user-select: none; pointer-events: none; }
.map-image--empty { display: grid; place-items: center; color: #aaa; min-height: 240px; }
.map-marker { position: absolute; display: grid; place-items: center; width: 34px; height: 34px; padding: 0; border: 2px solid #fff; border-radius: 50% 50% 50% 0; transform: translate(-50%, -100%) rotate(-45deg); background: #c46a2a; color: #fff; cursor: pointer; box-shadow: 0 2px 8px #0008; }
.map-marker--character, .map-marker--hero { background: #2f6bff; }
.map-marker--location { background: #0f9d76; }
.map-marker--item { background: #b9770f; }
.map-marker__icon { width: 19px; height: 19px; transform: rotate(45deg); }
.map-marker__image { width: 100%; height: 100%; object-fit: cover; border-radius: 50% 50% 50% 0; transform: rotate(45deg); }
.map-marker--cluster { display: grid; place-items: center; background: #c46a2a; color: #fff; font-weight: 700; border-radius: 50%; transform: translate(-50%, -50%); }
.map-marker__dot { display: block; width: 100%; height: 100%; border-radius: 50% 50% 50% 0; }
.map-modal { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 20px; background: rgba(0,0,0,.45); }
.map-modal__card { width: min(420px, 100%); }
</style>
