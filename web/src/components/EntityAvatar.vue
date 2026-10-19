<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { fetchFileBlob } from '@/api'
import type { LocalObject } from '@/db/schema'
import { typeIconPath, type IconKind } from '@/ui/typeIcons'

/**
 * Кэш object URL по файлу: один и тот же аватар в списке и карточке
 * скачивается один раз. Ссылки живут до перезагрузки страницы.
 */
const AVATAR_URLS = new Map<string, string>()
const AVATAR_LOADING = new Map<string, Promise<string | null>>()

async function avatarUrl(fileId: string): Promise<string | null> {
  const ready = AVATAR_URLS.get(fileId)
  if (ready) return ready
  let pending = AVATAR_LOADING.get(fileId)
  if (!pending) {
    pending = fetchFileBlob(fileId)
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        AVATAR_URLS.set(fileId, url)
        return url
      })
      .catch(() => null)
      .finally(() => AVATAR_LOADING.delete(fileId))
    AVATAR_LOADING.set(fileId, pending)
  }
  return pending
}

/**
 * Аватар объекта на сайте: загруженная картинка либо силуэт категории,
 * окрашенный по расе или типу, и значок класса.
 *
 * Логика та же, что в приложении: одинаковые расы и типы дают одинаковый цвет,
 * «Неизвестно» и «Без …» — нейтральный серый.
 */
const props = withDefaults(
  defineProps<{
    object: LocalObject
    size?: number
    /** Показывать значок класса (герой и персонаж). */
    showBadge?: boolean
  }>(),
  { size: 40, showBadge: true },
)

const PALETTE = ['#5B7C99', '#8A6A3B', '#4F6F5A', '#6B5A7A', '#B98B2E']

function hashColor(key: string | null | undefined): string {
  const value = (key ?? '').trim().toLowerCase()
  if (value === '' || value.startsWith('неизвест') || value.startsWith('без ')) return '#8A8A8A'
  let hash = 0
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) | 0
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

const coverId = computed(() => props.object.avatarFileId)

const coverUrl = ref<string | null>(null)
watch(
  coverId,
  async (fileId) => {
    coverUrl.value = null
    if (!fileId) return
    const url = await avatarUrl(fileId)
    if (coverId.value === fileId) coverUrl.value = url
  },
  { immediate: true },
)

const color = computed(() => {
  if (props.object.objectType === 'LOCATION') return hashColor(props.object.locationType)
  if (props.object.objectType === 'ITEM') return hashColor(props.object.itemType)
  return hashColor((props.object as { race?: string | null }).race)
})

/** Раса, тип локации или тип предмета — по ним выбирается глиф. */
const iconKind = computed<IconKind>(() => {
  if (props.object.objectType === 'LOCATION') return 'LOCATION'
  if (props.object.objectType === 'ITEM') return 'ITEM'
  return 'RACE'
})

const iconValue = computed(() => {
  if (props.object.objectType === 'LOCATION') return props.object.locationType
  if (props.object.objectType === 'ITEM') return props.object.itemType
  return (props.object as { race?: string | null }).race
})

const iconPath = computed(() => typeIconPath(iconKind.value, iconValue.value))

const badge = computed(() => {
  if (!props.showBadge) return ''
  const klass = (props.object as { klass?: string | null }).klass
  if (!klass || klass.trim() === '') return ''
  if (props.object.objectType !== 'HERO' && props.object.objectType !== 'CHARACTER') return ''
  return klass.trim().slice(0, 1).toUpperCase()
})
</script>

<template>
  <span
    class="avatar"
    :style="{ width: `${size}px`, height: `${size}px`, background: `${color}22`, color }"
    :title="object.name"
  >
    <img v-if="coverUrl" class="avatar__img" :src="coverUrl" :alt="object.name" />
    <svg
      v-else
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.7"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path :d="iconPath" />
    </svg>
    <span v-if="badge" class="avatar__badge">{{ badge }}</span>
  </span>
</template>

<style scoped>
.avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 50%;
}
.avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}
.avatar svg {
  width: 62%;
  height: 62%;
}
.avatar__badge {
  position: absolute;
  right: -2px;
  bottom: -2px;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 8px;
  background: var(--bg-elev);
  border: 1px solid currentColor;
  color: inherit;
  font-size: 0.62rem;
  font-weight: 700;
  line-height: 14px;
  text-align: center;
}
</style>
