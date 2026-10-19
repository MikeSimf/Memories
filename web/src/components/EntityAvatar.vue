<script setup lang="ts">
import { computed } from 'vue'

import type { LocalObject } from '@/db/schema'

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

const color = computed(() => {
  if (props.object.objectType === 'LOCATION') return hashColor(props.object.locationType)
  if (props.object.objectType === 'ITEM') return hashColor(props.object.itemType)
  return hashColor((props.object as { race?: string | null }).race)
})

const category = computed(() => {
  if (props.object.objectType === 'LOCATION') return 'location'
  if (props.object.objectType === 'ITEM') return 'item'
  return 'person'
})

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
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
      <template v-if="category === 'person'">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21c0-4.2 3.6-7 8-7s8 2.8 8 7" stroke-linecap="round" />
      </template>
      <template v-else-if="category === 'location'">
        <path d="M12 3l5 7h-3l4 6H6l4-6H7z" stroke-linejoin="round" />
        <path d="M12 16v5" stroke-linecap="round" />
      </template>
      <template v-else>
        <path d="M14 3l7 7-8 8-4-4z" stroke-linejoin="round" />
        <path d="M6 15l3 3-4 3z" stroke-linejoin="round" />
      </template>
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
