<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { crashesApi, errorMessage, type CrashReportDto } from '@/api'

const items = ref<CrashReportDto[]>([])
const error = ref<string | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    items.value = await crashesApi.list()
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
        <h1>Падения приложения</h1>
        <div class="page-header__subtitle">Отчёты, присланные с телефонов</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'settings' }">Настройки</RouterLink>
    </div>
    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем…</div>
    <p v-else-if="error" class="notice notice--danger">{{ error }}</p>
    <p v-else-if="items.length === 0" class="faint">Падений пока нет.</p>
    <div v-else class="stack stack--sm">
      <div v-for="item in items" :key="item.id" class="card stack stack--sm">
        <div class="row row--between">
          <strong>{{ item.appVersion || 'версия неизвестна' }}</strong>
          <span class="faint">{{ new Date(item.createdAt).toLocaleString('ru') }}</span>
        </div>
        <div v-if="item.device" class="faint">{{ item.device }}</div>
        <pre style="white-space: pre-wrap; font-size: 0.75rem">{{ item.stacktrace }}</pre>
      </div>
    </div>
  </div>
</template>
