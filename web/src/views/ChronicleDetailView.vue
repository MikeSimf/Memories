<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { chroniclesApi, errorMessage, type ChronicleDto } from '@/api'
function chronicleDate(value: string | null): string {
  if (!value) return 'без даты'
  const [year, month, day] = value.slice(0, 10).split('-')
  return `${day}.${month}.${year}`
}

const route = useRoute()
const router = useRouter()
const campaignId = computed(() => String(route.params.campaignId ?? ''))
const chronicleId = computed(() => String(route.params.chronicleId ?? ''))
const chronicle = ref<ChronicleDto | null>(null)
const error = ref<string | null>(null)

function pointLabel(point: ChronicleDto['points'][number]): string {
  return `${point.mapName} · ${point.wholeTeam ? 'вся команда' : point.heroName || 'герой'} · порядок ${point.order}${point.teleport ? ' · телепорт' : ''}`
}

onMounted(async () => {
  try {
    chronicle.value = await chroniclesApi.get(campaignId.value, chronicleId.value)
  } catch (caught) {
    error.value = errorMessage(caught)
  }
})
</script>

<template>
  <section class="stack">
    <div class="row">
      <button class="btn btn--sm" type="button" @click="router.push({ name: 'campaign-chronicles', params: { campaignId } })">← К хроникам</button>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <div v-if="chronicle" class="stack">
      <h2 style="margin: 0">{{ chronicle.name }}</h2>
      <p class="faint">{{ chronicleDate(chronicle.date) }}</p>

      <div class="card stack">
        <h3>Истории</h3>
        <ul v-if="chronicle.retellings.length > 0">
          <li v-for="story in chronicle.retellings" :key="story.id">
            <RouterLink :to="{ name: 'retelling-detail', params: { campaignId, retellingId: story.id } }">{{ story.title || 'Без названия' }}</RouterLink>
          </li>
        </ul>
        <p v-else class="faint">Нет историй, привязанных к этой хронике.</p>
      </div>

      <div class="card stack">
        <h3>Координаты</h3>
        <ul v-if="chronicle.points.length > 0">
          <li v-for="point in chronicle.points" :key="point.id">{{ pointLabel(point) }}</li>
        </ul>
        <p v-else class="faint">Нет координат.</p>
      </div>
    </div>
    <div v-else-if="!error" class="loading"><span class="spinner" /> Загружаем хронику…</div>
  </section>
</template>
