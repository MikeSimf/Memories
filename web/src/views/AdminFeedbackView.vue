<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { errorMessage, feedbackApi, type FeedbackDto } from '@/api'

/** Обращения обратной связи — только для админа (проверяет сервер). */
const items = ref<FeedbackDto[]>([])
const error = ref<string | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    items.value = await feedbackApi.list()
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Обращения</h1>
        <div class="page-header__subtitle">Обратная связь пользователей</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'settings' }">Настройки</RouterLink>
    </div>

    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем…</div>
    <p v-else-if="error" class="notice notice--danger">{{ error }}</p>
    <p v-else-if="items.length === 0" class="faint">Обращений пока нет.</p>
    <div v-else class="stack stack--sm">
      <div v-for="item in items" :key="item.id" class="card stack stack--sm">
        <div class="row row--between">
          <strong>{{ item.email }}</strong>
          <span class="faint">{{ new Date(item.createdAt).toLocaleString('ru') }}</span>
        </div>
        <p style="margin: 0; white-space: pre-wrap">{{ item.message }}</p>
        <span v-if="item.page" class="faint">Страница: {{ item.page }}</span>
      </div>
    </div>
  </div>
</template>
