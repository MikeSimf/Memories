<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError, apiBaseUrl, errorMessage, serviceApi } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { countLocalChanges } from '@/db'
import { useAuthStore } from '@/stores/auth'
import { useUiStore, type ThemeMode } from '@/stores/ui'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const displayName = ref(auth.user?.displayName ?? '')
const email = ref(auth.user?.email ?? '')
const phone = ref(auth.user?.phone ?? '')
const saving = ref(false)
const loggingOut = ref(false)
const version = ref<string | null>(null)
const confirmLogout = ref(false)
const unsynced = ref({ drafts: 0, pending: 0 })

const currentPassword = ref('')
const newPassword = ref('')
const repeatPassword = ref('')
const savingPassword = ref(false)
const profileError = ref<string | null>(null)
const passwordError = ref<string | null>(null)

const themes: Array<{ value: ThemeMode; label: string }> = [
  { value: 'system', label: 'Как в системе' },
  { value: 'light', label: 'Светлая' },
  { value: 'dark', label: 'Тёмная' },
]

const baseUrl = computed(() => apiBaseUrl() || '(относительный путь /api — за nginx)')
const hasPassword = computed(() => auth.user?.hasPassword === true)
const canSaveProfile = computed(
  () => displayName.value.trim() !== '' && !saving.value,
)

// Профиль мог обновиться после входа или синхронизации — подтягиваем значения.
watch(
  () => auth.user,
  (user) => {
    if (!user) return
    displayName.value = user.displayName
    email.value = user.email ?? ''
    phone.value = user.phone ?? ''
  },
)

/**
 * Понятные сообщения по кодам контракта (`api.md` §1, §3): занятые почта и телефон
 * приходят как `409`, истёкшая сессия — как `401`.
 */
function contactError(caught: unknown): string {
  if (caught instanceof ApiError) {
    switch (caught.code) {
      case 'email_taken':
        return 'Эта почта уже привязана к другому аккаунту — укажите другую.'
      case 'phone_taken':
        return 'Этот телефон уже привязан к другому аккаунту — укажите другой.'
      case 'unauthorized':
        return 'Сессия истекла — войдите заново, изменения не сохранены.'
      case 'validation_failed':
        return 'Проверьте почту и телефон: сервер не принял значения.'
      default:
        return errorMessage(caught)
    }
  }
  return errorMessage(caught)
}

onMounted(async () => {
  if (auth.user) {
    displayName.value = auth.user.displayName
    email.value = auth.user.email ?? ''
    phone.value = auth.user.phone ?? ''
  }
  try {
    const info = await serviceApi.version()
    version.value = `${info.version} (${info.profile})`
  } catch {
    version.value = null
  }
})

async function saveProfile(): Promise<void> {
  saving.value = true
  profileError.value = null
  try {
    await auth.updateProfile({
      displayName: displayName.value.trim(),
      email: email.value.trim(),
      phone: phone.value.trim(),
    })
    ui.pushToast('success', 'Профиль обновлён')
  } catch (caught) {
    profileError.value = contactError(caught)
    ui.pushToast('error', profileError.value)
  } finally {
    saving.value = false
  }
}

async function savePassword(): Promise<void> {
  passwordError.value = null
  if (newPassword.value.length < 8) {
    passwordError.value = 'Пароль от 8 символов'
    return
  }
  if (newPassword.value !== repeatPassword.value) {
    passwordError.value = 'Пароли не совпадают'
    return
  }
  if (hasPassword.value && currentPassword.value === '') {
    passwordError.value = 'Укажите текущий пароль'
    return
  }
  savingPassword.value = true
  try {
    await auth.setPassword({
      currentPassword: hasPassword.value ? currentPassword.value : undefined,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    repeatPassword.value = ''
    ui.pushToast('success', hasPassword.value ? 'Пароль изменён' : 'Пароль задан — вход в веб-версию доступен')
  } catch (caught) {
    passwordError.value = contactError(caught)
    ui.pushToast('error', passwordError.value)
  } finally {
    savingPassword.value = false
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
        <div class="page-header__subtitle">Профиль, вход по паролю, тема оформления и данные приложения</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'campaigns' }">К кампаниям</RouterLink>
    </div>

    <form class="card stack" @submit.prevent="saveProfile">
      <h2>Профиль</h2>
      <div class="row" style="align-items: flex-end">
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="profile-name">Имя</label>
          <input id="profile-name" v-model="displayName" class="input" type="text" />
        </div>
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="profile-phone">Телефон</label>
          <input
            id="profile-phone"
            v-model="phone"
            class="input"
            type="tel"
            inputmode="tel"
            placeholder="+7 999 123-45-67"
          />
        </div>
      </div>
      <div class="field">
        <label class="field__label" for="profile-email">Почта</label>
        <input
          id="profile-email"
          v-model="email"
          class="input"
          type="email"
          placeholder="gm@example.com"
        />
        <span class="field__hint">По почте или телефону вы входите в веб-версию с любого устройства.</span>
      </div>

      <p v-if="profileError" class="notice notice--danger">{{ profileError }}</p>

      <div class="row row--end">
        <button class="btn btn--primary" type="submit" :disabled="!canSaveProfile">
          <span v-if="saving" class="spinner" aria-hidden="true" />
          Сохранить
        </button>
      </div>
    </form>

    <form class="card stack" @submit.prevent="savePassword">
      <div class="row row--between">
        <h2 style="margin: 0">Пароль</h2>
        <span class="badge" :class="hasPassword ? 'badge--success' : 'badge--warning'">
          {{ hasPassword ? 'пароль задан' : 'пароль не задан' }}
        </span>
      </div>
      <p class="notice notice--accent" style="margin: 0">
        Укажите почту или телефон и задайте пароль — сможете входить с компьютера и телефона.
      </p>

      <div class="field" v-if="hasPassword">
        <label class="field__label" for="profile-current-password">Текущий пароль</label>
        <input
          id="profile-current-password"
          v-model="currentPassword"
          class="input"
          type="password"
          autocomplete="current-password"
        />
      </div>
      <div class="row" style="align-items: flex-end">
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="profile-new-password">
            {{ hasPassword ? 'Новый пароль' : 'Пароль' }}
          </label>
          <input
            id="profile-new-password"
            v-model="newPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            minlength="8"
          />
        </div>
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="profile-repeat-password">Повторите пароль</label>
          <input
            id="profile-repeat-password"
            v-model="repeatPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            minlength="8"
          />
        </div>
      </div>
      <span class="field__hint">Пароль от 8 символов. Телефон хранится в виде +7XXXXXXXXXX.</span>

      <p v-if="passwordError" class="notice notice--danger">{{ passwordError }}</p>

      <div class="row row--end">
        <button
          class="btn btn--primary"
          type="submit"
          :disabled="savingPassword || newPassword.length < 8"
        >
          <span v-if="savingPassword" class="spinner" aria-hidden="true" />
          {{ hasPassword ? 'Сменить пароль' : 'Задать пароль' }}
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
