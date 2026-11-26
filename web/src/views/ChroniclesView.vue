<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { chroniclesApi, errorMessage, type ChronicleDto } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import { useCampaignStore } from '@/stores/campaign'
import { todayIso } from '@/utils/datetime'

const route = useRoute()
const campaign = useCampaignStore()
const campaignId = computed(() => String(route.params.campaignId ?? ''))
const chronicles = ref<ChronicleDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const editing = ref<ChronicleDto | null>(null)
const name = ref('')
const date = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    chronicles.value = await chroniclesApi.list(campaignId.value)
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

function startCreate(): void {
  editing.value = { id: '', name: '', date: todayIso(), retellings: [], points: [] }
  name.value = ''
  date.value = todayIso()
}

function startEdit(item: ChronicleDto): void {
  editing.value = item
  name.value = item.name
  date.value = item.date ?? ''
}

async function save(): Promise<void> {
  if (name.value.trim() === '') {
    error.value = 'Укажите название хроники'
    return
  }
  try {
    if (editing.value?.id) await chroniclesApi.update(campaignId.value, editing.value.id, { name: name.value.trim(), date: date.value || null })
    else await chroniclesApi.create(campaignId.value, { name: name.value.trim(), date: date.value || null })
    editing.value = null
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function remove(item: ChronicleDto): Promise<void> {
  if (!confirm(`Удалить хронику «${item.name}»?`)) return
  try {
    await chroniclesApi.remove(campaignId.value, item.id)
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

onMounted(load)
</script>

<template>
  <section class="stack">
    <div class="row row--between">
      <h2 style="margin: 0">Хроника</h2>
      <button v-if="campaign.canCreate('LOCATION')" class="btn btn--primary btn--sm" type="button" @click="startCreate">+ Хроника</button>
    </div>

    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем хроники…</div>
    <EmptyState v-else-if="chronicles.length === 0" title="Хроник пока нет" hint="Создайте хронику сессии, привяжите к ней пересказы и точки на картах." />

    <div v-else class="stack">
      <article v-for="item in chronicles" :key="item.id" class="card stack">
        <div class="row row--between">
          <h3 style="margin: 0">{{ item.name }}</h3>
          <span class="faint">{{ item.date || 'без даты' }}</span>
        </div>
        <div v-if="item.retellings.length > 0">
          <span class="field__label">Пересказы</span>
          <ul>
            <li v-for="retelling in item.retellings" :key="retelling.id">
              <RouterLink :to="{ name: 'retelling-detail', params: { campaignId, retellingId: retelling.id } }">{{ retelling.title || 'Без названия' }}</RouterLink>
            </li>
          </ul>
        </div>
        <p v-else class="faint">Пересказов с этой хроникой нет.</p>
        <div v-if="item.points.length > 0">
          <span class="field__label">Координаты</span>
          <ul>
            <li v-for="point in item.points" :key="point.id">
              {{ point.mapName }} · {{ point.wholeTeam ? 'вся команда' : point.heroName }} · порядок {{ point.order }}<span v-if="point.teleport"> · телепорт</span>
            </li>
          </ul>
        </div>
        <p v-else class="faint">Точек на картах нет.</p>
        <div class="row">
          <button class="btn btn--sm" type="button" @click="startEdit(item)">Изменить</button>
          <button class="btn btn--danger btn--sm" type="button" @click="remove(item)">Удалить</button>
        </div>
      </article>
    </div>

    <div v-if="editing" class="card stack">
      <h3>{{ editing.id ? 'Правка хроники' : 'Новая хроника' }}</h3>
      <div class="field">
        <label class="field__label" for="chronicle-name">Название</label>
        <input id="chronicle-name" v-model="name" class="input" type="text" />
      </div>
      <div class="field">
        <label class="field__label" for="chronicle-date">Дата</label>
        <input id="chronicle-date" v-model="date" class="input" type="date" />
      </div>
      <div class="row">
        <button class="btn btn--primary" type="button" @click="save">Сохранить</button>
        <button class="btn" type="button" @click="editing = null">Отмена</button>
      </div>
    </div>
  </section>
</template>
