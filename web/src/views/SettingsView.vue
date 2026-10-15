<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { apiBaseUrl, authApi, errorMessage, serviceApi } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { countLocalChanges } from '@/db'
import { useAuthStore } from '@/stores/auth'
import { useUiStore, type ThemeMode } from '@/stores/ui'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const displayName = ref(auth.user?.displayName ?? '')
const saving = ref(false)
const loggingOut = ref(false)
const version = ref<string | null>(null)
const confirmLogout = ref(false)
const unsynced = ref({ drafts: 0, pending: 0 })

const themes: Array<{ value: ThemeMode; label: string }> = [
  { value: 'system', label: 'Как в системе' },
  { value: 'light', label: 'Светлая' },
  { value: 'dark', label: 'Тёмная' },
]

const baseUrl = computed(() => apiBaseUrl() || '(относительный путь /api — за nginx)')

onMounted(async () => {
  if (auth.user) displayName.value = auth.user.displayName
  try {
    const info = await serviceApi.version()
    version.value = `${info.version} (${info.profile})`
  } catch {
    version.value = null
  }
})

async function saveProfile(): Promise<void> {
  saving.value = true
  try {
    await authApi.patchMe({ displayName: displayName.value.trim() })
    await auth.refreshProfile()
    ui.pushToast('success', 'Профиль обновлён')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    saving.value = false
  }
}

/** Перед выходом предупреждаем, если на устройстве остались несохранённые данные. */
async function requestLogout(): Promise<void> {
  try {
    unsynced.value = await countLocalChanges()
  } catch {
    unsynced.value = { drafts: 0, pending: 0 }
  }
  if (unsynced.value.drafts > 0 || unsynced.value.pending > 0) {
    confirmLogout.value = true
    return
  }
  await logout()
}

async function logout(): Promise<void> {
  loggingOut.value = true
  try {
    await auth.logout()
    confirmLogout.value = false
    ui.pushToast('info', 'Вы вышли из аккаунта')
    await router.push({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Настройки</h1>
        <div class="page-header__subtitle">Профиль, тема оформления и данные приложения</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'campaigns' }">К кампаниям</RouterLink>
    </div>

    <form class="card stack" @submit.prevent="saveProfile">
      <h2>Профиль</h2>
      <div class="field">
        <label class="field__label" for="profile-name">Имя</label>
        <input id="profile-name" v-model="displayName" class="input" type="text" />
      </div>
      <div class="field">
        <label class="field__label" for="profile-email">Почта</label>
        <input id="profile-email" class="input" type="email" readonly :value="auth.user?.email ?? ''" />
      </div>
      <div class="row row--end">
        <button class="btn btn--primary" type="submit" :disabled="saving || displayName.trim() === ''">
          <span v-if="saving" class="spinner" aria-hidden="true" />
          Сохранить
        </button>
      </div>
    </form>

    <div class="card stack">
      <h2>Тема</h2>
      <div class="row">
        <label v-for="theme in themes" :key="theme.value" class="chip" style="cursor: pointer">
          <input
            type="radio"
            name="theme"
            :value="theme.value"
            :checked="ui.themeMode === theme.value"
            @change="ui.setTheme(theme.value)"
          />
          {{ theme.label }}
        </label>
      </div>
    </div>

    <div class="card stack">
      <h2>Приложение</h2>
      <div class="faint">
        <div>Адрес API: {{ baseUrl }}</div>
        <div v-if="version">Версия сервера: {{ version }}</div>
        <div>Работает офлайн: данные хранятся в IndexedDB, приложение открывается без сети.</div>
      </div>
      <p class="faint">
        Чтобы установить приложение на телефон, откройте сайт в браузере и выберите
        «Добавить на главный экран».
      </p>
    </div>

    <div class="card stack">
      <h2>Выход</h2>
      <p class="muted">
        При выходе локальная база устройства очищается — данные чужого аккаунта не остаются.
        Черновики пересказов живут только на устройстве, поэтому выгрузите их (опубликуйте)
        до выхода.
      </p>
      <div class="row">
        <button class="btn btn--danger" type="button" :disabled="loggingOut" @click="requestLogout">
          <span v-if="loggingOut" class="spinner" aria-hidden="true" />
          Выйти из аккаунта
        </button>
      </div>
    </div>

    <ConfirmDialog
      :open="confirmLogout"
      :busy="loggingOut"
      title="Выйти без сохранения?"
      :message="`На устройстве осталось черновиков: ${unsynced.drafts}, неотправленных правок: ${unsynced.pending}. После выхода они будут удалены.`"
      confirm-label="Выйти и удалить"
      @cancel="confirmLogout = false"
      @confirm="logout"
    />
  </div>
</template>
