<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
const dragMoved = ref(false)
const hovered = ref<{ x: number; y: number; text: string } | null>(null)
const search = ref('')
const showNames = ref(false)
const canvasEl = ref<HTMLElement | null>(null)
const imageW = ref(0)
const imageH = ref(0)
const pick = ref<{ x: number; y: number } | null>(null)
const chosenEntityId = ref('')
const selectedMarker = ref<MapMarkerDto | null>(null)
const filters = ref<Record<string, boolean>>({ HERO: true, CHARACTER: true, LOCATION: true, ITEM: true })
const objectTypes = [
  { key: 'CHARACTER', label: 'Персонажи' },
  { key: 'HERO', label: 'Герои' },
  { key: 'LOCATION', label: 'Локации' },
  { key: 'ITEM', label: 'Предметы' },
]

const canEdit = computed(() => campaign.canCreate('LOCATION'))

const listedMarkers = computed(() => (map.value?.markers ?? []).filter((marker) => filters.value[marker.objectType]))

const groupedMarkers = computed(() => {
  const order = ['CHARACTER', 'HERO', 'LOCATION', 'ITEM']
  const labels: Record<string, string> = { CHARACTER: 'Персонажи', HERO: 'Герои', LOCATION: 'Локации', ITEM: 'Предметы' }
  return order
    .map((type) => ({ type, label: labels[type], markers: listedMarkers.value.filter((marker) => marker.objectType === type) }))
    .filter((group) => group.markers.length > 0)
})

const markerGroups = computed(() => {
  if (!map.value) return []
  const cell = scale.value >= 1.8 ? 0.001 : 0.06
  const groups = new Map<string, MapMarkerDto[]>()
  for (const marker of map.value.markers.filter((item) => filters.value[item.objectType])) {
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
  const query = search.value.trim().toLowerCase()
  return campaign.objects
    .filter((object) => !object.deleted && !used.has(object.id))
    .filter((object) => !query || object.name.toLowerCase().includes(query))
    .sort((a, b) => a.name.localeCompare(b.name, 'ru'))
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

function refreshSize(): void {
  const img = canvasEl.value?.querySelector<HTMLImageElement>('.map-image img')
  if (img) {
    imageW.value = img.clientWidth
    imageH.value = img.clientHeight
  }
}

function screenX(x: number): number {
  return tx.value + x * imageW.value * scale.value
}

function screenY(y: number): number {
  return ty.value + y * imageH.value * scale.value
}

function markerStyle(group: { x: number; y: number }): Record<string, string> {
  return { left: `${screenX(group.x)}px`, top: `${screenY(group.y)}px` }
}

function nameStyle(group: { x: number; y: number }): Record<string, string> {
  return { left: `${screenX(group.x)}px`, top: `${screenY(group.y)}px` }
}

function zoom(delta: number): void {
  scale.value = Math.max(1, Math.min(4, scale.value + delta))
}

function wheel(event: WheelEvent): void {
  event.preventDefault()
  const next = Math.max(1, Math.min(4, scale.value + (event.deltaY < 0 ? 0.15 : -0.15)))
  if (next === scale.value) return
  const canvas = document.querySelector<HTMLElement>('.map-canvas')
  if (!canvas) { scale.value = next; return }
  const rect = canvas.getBoundingClientRect()
  const dx = event.clientX - rect.left
  const dy = event.clientY - rect.top
  const ratio = next / scale.value
  tx.value += dx * (1 - ratio)
  ty.value += dy * (1 - ratio)
  scale.value = next
}

function startDrag(event: PointerEvent): void {
  if ((event.target as HTMLElement).closest('.map-marker')) return
  event.preventDefault()
  dragging.value = true
  dragMoved.value = false
  dragStart.value = { x: event.clientX, y: event.clientY, tx: tx.value, ty: ty.value }
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
}

function moveDrag(event: PointerEvent): void {
  if (!dragging.value) return
  const dx = event.clientX - dragStart.value.x
  const dy = event.clientY - dragStart.value.y
  if (Math.abs(dx) > 4 || Math.abs(dy) > 4) dragMoved.value = true
  tx.value = dragStart.value.tx + dx
  ty.value = dragStart.value.ty + dy
}

function endDrag(): void {
  dragging.value = false
}

function mapClick(event: MouseEvent): void {
  if (!adding.value || !map.value || dragMoved.value) return
  const image = (event.currentTarget as HTMLElement).querySelector<HTMLElement>('.map-image img')
  if (!image) return
  const rect = image.getBoundingClientRect()
  pick.value = {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
  }
  chosenEntityId.value = ''
}

async function addMarker(): Promise<void> {
  if (!pick.value || !chosenEntityId.value) return
  try {
    const marker = await mapsApi.addMarker(campaignId, mapId, chosenEntityId.value, pick.value.x, pick.value.y)
    map.value?.markers.push(marker)
    if (map.value) map.value.markerCount = map.value.markers.length
    pick.value = null
    adding.value = false
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function deleteMarker(): Promise<void> {
  if (!selectedMarker.value) return
  try {
    const id = selectedMarker.value.id
    await mapsApi.removeMarker(campaignId, mapId, id)
    if (map.value) {
      map.value.markers = map.value.markers.filter((marker) => marker.id !== id)
      map.value.markerCount = map.value.markers.length
    }
    selectedMarker.value = null
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
  if (marker.objectType === 'LOCATION') return typeIconPath('LOCATION', marker.locationType ?? '')
  return typeIconPath('ITEM', marker.itemType ?? '')
}

function markerClass(marker: MapMarkerDto): string {
  return `map-marker--${marker.objectType.toLowerCase()}`
}

function objectTypeLabel(type: string): string {
  return { HERO: 'Герой', CHARACTER: 'Персонаж', LOCATION: 'Локация', ITEM: 'Предмет' }[type] ?? type
}

let observer: ResizeObserver | null = null

onMounted(async () => {
  await load()
  await nextTick()
  refreshSize()
  if (canvasEl.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(refreshSize)
    observer.observe(canvasEl.value)
  }
})

onBeforeUnmount(() => observer?.disconnect())
watch(() => map.value?.imageFileId, async () => {
  await nextTick()
  refreshSize()
})
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
      <div class="row map-controls">
        <button class="btn btn--sm" type="button" @click="scale = 1; tx = 0; ty = 0">Сбросить</button>
        <button v-if="canEdit" class="btn btn--sm" :class="{ 'btn--primary': adding }" type="button" @click="adding = !adding">
          {{ adding ? 'Выберите точку на карте' : 'Добавить метку' }}
        </button>
        <span class="map-filters">
          <label v-for="type in objectTypes" :key="type.key">
            <input v-model="filters[type.key]" type="checkbox" /> {{ type.label }}
          </label>
          <label><input v-model="showNames" type="checkbox" /> Названия</label>
        </span>
      </div>

      <div class="map-layout">
      <div class="map-stage">
      <div
        class="map-viewport"
        :class="{ 'map-viewport--adding': adding }"
        @wheel="wheel"
        @dragstart.prevent
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
        @click="mapClick"
      >
        <div ref="canvasEl" class="map-canvas">
          <div v-if="map.imageFileId" class="map-image" :style="{ transform: `translate(${tx}px, ${ty}px) scale(${scale})` }"><EntityImage :file-id="map.imageFileId" alt="" :interactive="false" :max-height="0" /></div>
          <div v-else class="map-image map-image--empty">Фон не загружен</div>
          <button
            v-for="(group, index) in markerGroups"
            :key="index"
            class="map-marker"
            :class="[markerClass(group.markers[0]), { 'map-marker--cluster': group.markers.length > 1 }]"
            :style="markerStyle(group)"
            :title="group.markers.map((item) => item.name).join(', ')"
            type="button"
            @click.stop="openGroup(group)"
            @mouseenter="hovered = { x: group.x, y: group.y, text: group.markers.length > 1 ? group.markers.map((item) => item.name).join(', ') : group.markers[0].name + ' · ' + objectTypeLabel(group.markers[0].objectType) }"
            @mouseleave="hovered = null"
          >
            <template v-if="group.markers.length > 1">{{ group.markers.length }}</template>
            <svg v-else class="map-marker__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path :d="markerIcon(group.markers[0])" />
            </svg>
          </button>
          <div v-if="showNames" v-for="(group, index) in markerGroups" :key="'name-' + index" class="map-name" :style="nameStyle(group)">{{ group.markers.length > 1 ? group.markers.length + ' объектов' : group.markers[0].name }}</div>
          <div v-if="hovered" class="map-hover" :style="{ left: `${screenX(hovered.x)}px`, top: `${screenY(hovered.y)}px` }">{{ hovered.text }}</div>
        </div>
      </div>
        <div class="map-zoom">
          <button class="map-zoom__button" type="button" aria-label="Приблизить" @click="zoom(0.25)">+</button>
          <input v-model.number="scale" type="range" min="1" max="4" step="0.05" aria-label="Масштаб карты" />
          <button class="map-zoom__button" type="button" aria-label="Отдалить" @click="zoom(-0.25)">−</button>
        </div>
      </div>
      <aside class="map-side">
        <strong>На карте</strong>
        <template v-for="group in groupedMarkers" :key="group.type">
          <div class="map-side__title">{{ group.label }} · {{ group.markers.length }}</div>
          <button
            v-for="marker in group.markers"
            :key="marker.id"
            class="map-side__item"
            type="button"
            @click="selectedMarker = marker"
          >
            <span class="map-side__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path :d="markerIcon(marker)" /></svg>
            </span>
            <span>{{ marker.name }}</span>
          </button>
        </template>
        <p v-if="listedMarkers.length === 0" class="faint">Нет меток выбранных типов.</p>
      </aside>
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
          <input v-model="search" class="input" placeholder="Поиск по объектам" />
          <select v-model="chosenEntityId" class="select">
            <option value="" disabled>Выберите объект</option>
            <option v-for="object in available" :key="object.id" :value="object.id">
              {{ object.name }} ({{ objectTypeLabel(object.objectType) }})
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
.map-layout { display: grid; grid-template-columns: minmax(0, 1fr) 240px; gap: 12px; align-items: start; }
.map-stage { display: flex; align-items: center; justify-content: center; gap: 10px; min-width: 0; }
.map-side { display: flex; flex-direction: column; gap: 4px; max-height: clamp(320px, calc(100vh - 330px), 680px); overflow: auto; border: 1px solid var(--border); border-radius: 12px; padding: 10px; background: var(--bg-elev); }
.map-side__title { margin: 8px 0 2px; font-size: .75rem; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--text-muted); }
.map-side__item { display: flex; align-items: center; gap: 8px; width: 100%; border: 0; background: transparent; color: inherit; text-align: left; padding: 6px; border-radius: 8px; cursor: pointer; font: inherit; }
.map-side__item:hover { background: rgba(127,127,127,.14); }
.map-side__icon { width: 18px; height: 18px; flex: none; }
.map-side__icon svg { width: 18px; height: 18px; }
.map-viewport.map-viewport--adding { cursor: crosshair; }
.map-viewport--adding .map-image, .map-viewport--adding .map-image :deep(img) { cursor: crosshair; }
.map-viewport { user-select: none; -webkit-user-select: none; flex: 0 1 auto; width: fit-content; max-width: calc(100% - 44px); min-width: 0; height: auto; max-height: clamp(320px, calc(100vh - 330px), 680px); overflow: hidden; border: 1px solid var(--border); border-radius: 12px; background: #1b1b1b; cursor: grab; touch-action: none; display: flex; align-items: center; justify-content: center; }
.map-zoom { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 4px; }
.map-zoom__button { width: 28px; height: 28px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-elev); color: inherit; font-size: 18px; line-height: 1; cursor: pointer; }
.map-zoom__button:hover { border-color: var(--accent); }
.map-zoom input { writing-mode: vertical-lr; direction: rtl; width: 22px; height: 220px; accent-color: var(--accent); }
.map-controls { flex-wrap: wrap; }
.map-filters { display: flex; flex-wrap: wrap; gap: 8px; font-size: .85rem; }
.map-filters label { display: inline-flex; gap: 4px; align-items: center; }
.map-canvas { position: relative; flex: 0 0 auto; max-width: 100%; max-height: 100%; }
.map-image { display: block; user-select: none; -webkit-user-select: none; position: relative; max-width: 100%; max-height: 100%; cursor: grab; transform-origin: 0 0; will-change: transform; }
.map-image :deep(img) { display: block; -webkit-user-drag: none; user-drag: none; max-width: 100%; max-height: calc(100vh - 330px); width: auto; height: auto; cursor: inherit; user-select: none; pointer-events: none; }
.map-image--empty { display: grid; place-items: center; color: #aaa; min-height: 240px; }
.map-marker { transform-origin: 50% 100%; position: absolute; display: grid; place-items: center; width: 34px; height: 34px; padding: 0; border: 2px solid #fff; border-radius: 50% 50% 50% 0; transform: translate(-50%, -100%) rotate(-45deg); background: #c46a2a; color: #fff; cursor: pointer; box-shadow: 0 2px 8px #0008; }
.map-marker--character, .map-marker--hero { background: #2f6bff; }
.map-marker--location { background: #0f9d76; }
.map-marker--item { background: #b9770f; }
.map-hover { position: absolute; z-index: 5; transform: translate(-50%, -140%); padding: 6px 10px; border-radius: 8px; background: rgba(20,20,20,.92); color: #fff; font-size: .85rem; white-space: nowrap; max-width: 280px; overflow: hidden; text-overflow: ellipsis; pointer-events: none; box-shadow: 0 4px 16px #0006; }
.map-marker__name { position: absolute; z-index: 4; transform-origin: top center; padding: 3px 6px; border-radius: 6px; background: rgba(20,20,20,.82); color: #fff; font-size: .75rem; white-space: nowrap; pointer-events: none; }
.map-name { position: absolute; z-index: 4; transform-origin: top center; padding: 2px 5px; border-radius: 5px; background: rgba(20,20,20,.75); color: #fff; font-size: .7rem; white-space: nowrap; pointer-events: none; }
.map-marker__icon { width: 19px; height: 19px; transform: rotate(45deg); }
.map-marker__image { width: 100%; height: 100%; object-fit: cover; border-radius: 50% 50% 50% 0; transform: rotate(45deg); }
.map-marker--cluster { display: grid; place-items: center; background: #c46a2a; color: #fff; font-weight: 700; border-radius: 50%; transform: translate(-50%, -50%); }
.map-marker__dot { display: block; width: 100%; height: 100%; border-radius: 50% 50% 50% 0; }
.map-modal { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 20px; background: rgba(0,0,0,.45); }
.map-modal__card { width: min(420px, 100%); }
</style>
