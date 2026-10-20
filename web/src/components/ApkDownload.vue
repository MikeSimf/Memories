<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { appApi } from '@/api'

/**
 * Ссылка на приложение для Android (APK).
 *
 * Сам файл лежит в корне сайта (`/dnd-memory.apk`), поэтому ссылка работает и на
 * рабочем, и на тестовом контуре без привязки к домену. Версию показываем из
 * `GET /api/app/latest`; без сети остаётся ссылка без номера.
 */
withDefaults(
  defineProps<{
    /** `button` — заметная кнопка, `link` — строка в тексте. */
    variant?: 'button' | 'link' | 'icon' | 'header'
  }>(),
  { variant: 'button' },
)

const version = ref<string | null>(null)

/** Приложение раздаётся с того же сайта: путь относительный, без домена. */
const href = '/dnd-memory.apk'

const isAndroid = computed(() =>
  typeof navigator !== 'undefined' && /android/i.test(navigator.userAgent),
)

onMounted(async () => {
  try {
    version.value = (await appApi.latest()).versionName
  } catch {
    // Без сети ссылка всё равно рабочая.
  }
})
</script>

<template>
  <a
    v-if="variant === 'link'"
    class="apk-link"
    :href="href"
    download
    aria-label="Скачать приложение DnD Memory для Android"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
      <path d="M6 9.6h12v7.1a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2z" />
      <path d="M8.4 9.6 7 6.7M15.6 9.6 17 6.7" stroke-linecap="round" />
      <circle cx="9.7" cy="13" r=".85" fill="currentColor" stroke="none" />
      <circle cx="14.3" cy="13" r=".85" fill="currentColor" stroke="none" />
      <path d="M4.5 11.8v4.6M19.5 11.8v4.6" stroke-linecap="round" />
    </svg>
    <span>Скачать APK для Android</span>
    <span v-if="version" class="apk-link__version">{{ version }}</span>
  </a>

  <a
    v-else-if="variant === 'header'"
    class="btn btn--ghost apk-header"
    :href="href"
    download
    aria-label="Скачать приложение DnD Memory для Android"
    title="Скачать APK для Android"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
      <path d="M6 9.6h12v7.1a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2z" />
      <path d="M8.4 9.6 7 6.7M15.6 9.6 17 6.7" stroke-linecap="round" />
      <circle cx="9.7" cy="13" r=".85" fill="currentColor" stroke="none" />
      <circle cx="14.3" cy="13" r=".85" fill="currentColor" stroke="none" />
      <path d="M4.5 11.8v4.6M19.5 11.8v4.6" stroke-linecap="round" />
    </svg>
    <span class="apk-header__label">APK</span>
  </a>

  <a
    v-else-if="variant === 'icon'"
    class="btn btn--ghost btn--icon"
    :href="href"
    download
    aria-label="Скачать приложение DnD Memory для Android"
    title="Приложение для Android (APK)"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
      <path d="M6 9.6h12v7.1a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2z" />
      <path d="M8.4 9.6 7 6.7M15.6 9.6 17 6.7" stroke-linecap="round" />
      <circle cx="9.7" cy="13" r=".85" fill="currentColor" stroke="none" />
      <circle cx="14.3" cy="13" r=".85" fill="currentColor" stroke="none" />
      <path d="M4.5 11.8v4.6M19.5 11.8v4.6" stroke-linecap="round" />
    </svg>
  </a>

  <a
    v-else
    class="btn apk-button"
    :href="href"
    download
    aria-label="Скачать приложение DnD Memory для Android"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
      <path d="M6 9.6h12v7.1a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2z" />
      <path d="M8.4 9.6 7 6.7M15.6 9.6 17 6.7" stroke-linecap="round" />
      <circle cx="9.7" cy="13" r=".85" fill="currentColor" stroke="none" />
      <circle cx="14.3" cy="13" r=".85" fill="currentColor" stroke="none" />
      <path d="M4.5 11.8v4.6M19.5 11.8v4.6" stroke-linecap="round" />
    </svg>
    <span>{{ isAndroid ? 'Скачать приложение' : 'Скачать APK для Android' }}</span>
    <span v-if="version" class="apk-button__version">{{ version }}</span>
  </a>
</template>

<style scoped>
.apk-link {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-muted);
  text-decoration: none;
}
.apk-link:hover {
  color: var(--accent, currentColor);
  text-decoration: underline;
}
.apk-link svg {
  width: 18px;
  height: 18px;
  flex: none;
}
.apk-link__version {
  opacity: 0.7;
  font-size: 0.8em;
}
.apk-button svg {
  width: 18px;
  height: 18px;
  flex: none;
}
.apk-header {
  gap: 0.35rem;
}
.apk-header svg {
  width: 18px;
  height: 18px;
  flex: none;
}
.apk-header__label {
  display: none;
}
@media (min-width: 640px) {
  .apk-header__label {
    display: inline;
  }
}
.apk-button__version {
  opacity: 0.65;
  font-size: 0.8em;
}
</style>
