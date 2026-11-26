<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage, questsApi, type QuestDto } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import ListSort from '@/components/ListSort.vue'
import { useCampaignStore } from '@/stores/campaign'

const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const campaignId = String(route.params.campaignId ?? '')
const quests = ref<QuestDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const sortField = ref<'name' | 'createdAt' | 'updatedAt'>('name')
const sortAsc = ref(true)
const sortOptions = [
  { value: 'name', label: 'по названию' },
  { value: 'createdAt', label: 'по дате добавления' },
  { value: 'updatedAt', label: 'по дате изменения' },
]
const sortedQuests = computed(() => {
  const list = [...quests.value]
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
  try { quests.value = await questsApi.list(campaignId) } catch (caught) { error.value = errorMessage(caught) } finally { loading.value = false }
}
async function remove(quest: QuestDto): Promise<void> {
  if (!confirm(`Удалить квест «${quest.name}»?`)) return
  try { await questsApi.remove(campaignId, quest.id); await load() } catch (caught) { error.value = errorMessage(caught) }
}
onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="row row--between">
      <h2>Квесты</h2>
      <ListSort v-model="sortField" v-model:ascending="sortAsc" :options="sortOptions" />
      <button v-if="campaign.canCreate('LOCATION')" class="btn btn--primary" type="button" @click="router.push({ name: 'quest-new', params: { campaignId } })">+ Квест</button>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем квесты…</div>
    <EmptyState v-else-if="quests.length === 0" title="Пока нет квестов" hint="Создайте первый квест и ведите историю его решения в комментариях." />
    <div v-else class="quests-grid">
      <article v-for="quest in sortedQuests" :key="quest.id" class="card stack">
        <RouterLink :to="{ name: 'quest-detail', params: { campaignId, questId: quest.id } }"><strong>{{ quest.name }}</strong></RouterLink>
        <p class="faint">{{ quest.description || 'Без описания' }}</p>
        <p class="faint">
          Автор: {{ quest.createdByName || 'неизвестно' }}
          <template v-if="quest.updatedByName && quest.updatedByName !== quest.createdByName"> · изменил(а): {{ quest.updatedByName }}</template>
        </p>
        <div class="row">
          <RouterLink class="btn btn--sm" :to="{ name: 'quest-detail', params: { campaignId, questId: quest.id } }">Открыть</RouterLink>
          <RouterLink v-if="campaign.canCreate('LOCATION')" class="btn btn--sm" :to="{ name: 'quest-edit', params: { campaignId, questId: quest.id } }">Изменить</RouterLink>
          <button v-if="campaign.canCreate('LOCATION')" class="btn btn--danger btn--sm" type="button" @click="remove(quest)">Удалить</button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.quests-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
</style>
