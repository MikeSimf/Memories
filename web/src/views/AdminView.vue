<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  adminApi,
  crashesApi,
  errorMessage,
  feedbackApi,
  type AdminUserDto,
  type CrashReportDto,
  type FeedbackDto,
} from '@/api'

const users = ref<AdminUserDto[]>([])
const feedback = ref<FeedbackDto[]>([])
const crashes = ref<CrashReportDto[]>([])
const error = ref<string | null>(null)
const link = ref<string | null>(null)
const loading = ref(true)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [u, f, c] = await Promise.all([adminApi.users(), feedbackApi.list(), crashesApi.list()])
    users.value = u
    feedback.value = f
    crashes.value = c
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function reset(user: AdminUserDto): Promise<void> {
  if (!user.email) return
  try {
    link.value = (await adminApi.createResetLink(user.email)).url
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Кабинет system</h1>
        <div class="page-header__subtitle">Пользователи, обращения, ошибки и ссылки сброса</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'admin-cards' }">Карточки</RouterLink>
      <RouterLink class="btn btn--sm" :to="{ name: 'settings' }">Настройки</RouterLink>
    </div>

    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем…</div>
    <p v-else-if="error" class="notice notice--danger">{{ error }}</p>
    <template v-else>
      <p v-if="link" class="notice notice--success" style="word-break: break-all">
        Ссылка сброса: {{ link }}
      </p>

      <div class="card stack">
        <h2>Пользователи <span class="faint">{{ users.length }}</span></h2>
        <div class="admin-table">
          <div class="admin-table__row admin-table__head">
            <span>Логин</span><span>Имя</span><span>Почта</span><span>Обновлён</span><span></span>
          </div>
          <div v-for="user in users" :key="user.id" class="admin-table__row">
            <span>{{ user.login }}</span>
            <span>{{ user.displayName }} <span v-if="user.superuser" class="badge">system</span></span>
            <span>{{ user.email || '—' }}</span>
            <span>{{ new Date(user.updatedAt).toLocaleDateString('ru') }}</span>
            <button class="btn btn--sm" type="button" :disabled="!user.email" @click="reset(user)">
              Ссылка сброса
            </button>
          </div>
        </div>
      </div>

      <div class="card stack">
        <h2>Обратная связь <span class="faint">{{ feedback.length }}</span></h2>
        <p v-if="feedback.length === 0" class="faint">Обращений нет.</p>
        <div v-for="item in feedback" :key="item.id" class="stack stack--sm">
          <strong>{{ item.email || 'без почты' }} · {{ new Date(item.createdAt).toLocaleString('ru') }}</strong>
          <p style="margin: 0; white-space: pre-wrap">{{ item.message }}</p>
        </div>
      </div>

      <div class="card stack">
        <h2>Ошибки <span class="faint">{{ crashes.length }}</span></h2>
        <p v-if="crashes.length === 0" class="faint">Ошибок нет.</p>
        <details v-for="item in crashes" :key="item.id" class="stack stack--sm">
          <summary>{{ item.appVersion || '—' }} · {{ item.device || '—' }} · {{ new Date(item.createdAt).toLocaleString('ru') }}</summary>
          <pre style="white-space: pre-wrap; font-size: .75rem">{{ item.stacktrace }}</pre>
        </details>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-table { display: flex; flex-direction: column; gap: 4px; overflow-x: auto; }
.admin-table__row { display: grid; grid-template-columns: 1.2fr 1.4fr 1.4fr .8fr 1fr; gap: 8px; align-items: center; min-width: 680px; }
.admin-table__head { font-weight: 700; color: var(--text-muted); }
</style>
