<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminNav from '@/components/AdminNav.vue'

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
const route = useRoute()
const section = computed(() => String(route.query.section ?? 'users'))

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

async function removeFeedback(id: string): Promise<void> {
  if (!confirm('Удалить обращение?')) return
  try { await adminApi.deleteFeedback(id); await load() } catch (caught) { error.value = errorMessage(caught) }
}

async function removeCrash(id: string): Promise<void> {
  if (!confirm('Удалить отчёт об ошибке?')) return
  try { await adminApi.deleteCrash(id); await load() } catch (caught) { error.value = errorMessage(caught) }
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
      <RouterLink class="btn btn--sm" :to="{ name: 'settings' }">Настройки</RouterLink>
    </div>

    <div class="admin-layout">
      <AdminNav />
      <div class="admin-layout__main stack">

    <div v-if="loading" class="loading"><span class="spinner" /> Загружаем…</div>
    <p v-else-if="error" class="notice notice--danger">{{ error }}</p>
    <template v-else>
      <p v-if="link" class="notice notice--success" style="word-break: break-all">
        Ссылка сброса: {{ link }}
      </p>

      <div v-if="section === 'users'" class="card stack">
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

      <div v-if="section === 'feedback'" class="card stack">
        <h2>Обратная связь <span class="faint">{{ feedback.length }}</span></h2>
        <div class="admin-table">
          <div class="admin-table__row admin-table__row--feedback admin-table__head">
            <span>Дата</span><span>Почта</span><span>Сообщение</span><span></span>
          </div>
          <div v-for="item in feedback" :key="item.id" class="admin-table__row admin-table__row--feedback">
            <span>{{ new Date(item.createdAt).toLocaleString('ru') }}</span>
            <span>{{ item.email || '—' }}</span>
            <span class="admin-table__text" :title="item.message">{{ item.message }}</span>
            <button class="btn btn--danger btn--sm" type="button" @click="removeFeedback(item.id)">Удалить</button>
          </div>
        </div>
      </div>

      <div v-if="section === 'crashes'" class="card stack">
        <h2>Ошибки <span class="faint">{{ crashes.length }}</span></h2>
        <div class="admin-table">
          <div class="admin-table__row admin-table__row--crashes admin-table__head">
            <span>Дата</span><span>Версия</span><span>Устройство</span><span></span>
          </div>
          <div v-for="item in crashes" :key="item.id" class="admin-table__row admin-table__row--crashes">
            <span>{{ new Date(item.createdAt).toLocaleString('ru') }}</span>
            <span>{{ item.appVersion || '—' }}</span>
            <span>{{ item.device || '—' }}</span>
            <button class="btn btn--danger btn--sm" type="button" @click="removeCrash(item.id)">Удалить</button>
          </div>
        </div>
      </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-table { display: flex; flex-direction: column; gap: 4px; overflow-x: auto; }
.admin-table__row--feedback { grid-template-columns: .9fr .9fr 3fr .5fr; }
.admin-table__row--crashes { grid-template-columns: 1fr .6fr 1.2fr .5fr; }
.admin-table__text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-table__row { display: grid; grid-template-columns: 1.2fr 1.4fr 1.4fr .8fr 1fr; gap: 8px; align-items: center; min-width: 680px; }
.admin-table__head { font-weight: 700; color: var(--text-muted); }
.admin-layout { display: grid; grid-template-columns: 200px minmax(0, 1fr); gap: 12px; align-items: start; }
.admin-layout__main { min-width: 0; }
@media (max-width: 760px) { .admin-layout { grid-template-columns: 1fr; } }
</style>
