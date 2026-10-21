<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { fetchFileBlob } from '@/api'
import type { LocalObject } from '@/db/schema'
import { classIconUrl } from '@/ui/classIcons'
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
  }>(),
  { size: 40 },
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

/** Пол меняет причёску у народов; у локаций и предметов его нет. */
const iconGender = computed(() =>
  props.object.objectType === 'HERO' || props.object.objectType === 'CHARACTER'
    ? props.object.gender
    : null,
)

const iconPath = computed(() => typeIconPath(iconKind.value, iconValue.value, iconGender.value))

/** Класс есть только у героя и персонажа; у остальных второго кружка нет. */
const klass = computed(() => {
  if (props.object.objectType !== 'HERO' && props.object.objectType !== 'CHARACTER') return null
  return props.object.klass
})

const classIcon = computed(() => classIconUrl(klass.value))
const classColor = computed(() => hashColor(klass.value))
</script>

<template>
  <span class="avatar-pair" :title="object.name">
    <span
      class="avatar"
      :style="{ width: `${size}px`, height: `${size}px`, background: `${color}22`, color }"
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
    </span>

    <!-- Второй кружок: класс. Такого же размера, как аватар. -->
    <span
      v-if="classIcon"
      class="avatar avatar--class"
      :style="{
        width: `${size}px`,
        height: `${size}px`,
        background: `${classColor}22`,
        color: classColor,
      }"
      :title="`Класс: ${klass}`"
      role="img"
      :aria-label="`Класс: ${klass}`"
    >
      <span
        class="class-icon"
        :style="{ maskImage: `url(${classIcon})`, WebkitMaskImage: `url(${classIcon})` }"
      />
    </span>
  </span>
</template>

<style scoped>
.avatar-pair {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
}
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
.avatar--class .class-icon {
  width: 60%;
  height: 60%;
  background: currentColor;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  -webkit-mask-size: contain;
  mask-size: contain;
}
</style>
