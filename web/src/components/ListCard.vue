<script setup lang="ts">
import { computed } from 'vue'

import type { CardTemplateDto } from '@/api'
import type { LocalObject } from '@/db/schema'
import EntityImage from '@/components/EntityImage.vue'
import { classIconUrl } from '@/ui/classIcons'
import { typeIconPath } from '@/ui/typeIcons'
import { genderLabel } from '@/types/gender'

const props = defineProps<{ object: LocalObject; template: CardTemplateDto }>()

function value(field: string): string {
  const object = props.object as unknown as Record<string, unknown>
  if (field === 'type') return object.objectType as string
  if (field === 'gender') return genderLabel(object.gender as string | null)
  if (field === 'alive') {
    return object.alive === true ? 'жив' : object.alive === false ? 'мёртв' : 'неизвестно'
  }
  if (field === 'magic') {
    return object.magic === true ? 'да' : object.magic === false ? 'нет' : 'неизвестно'
  }
  if (field === 'hasSetting') return object.hasSetting ? 'да' : 'нет'
  const raw = object[field]
  return raw == null ? '' : String(raw)
}

function iconKind(field: string): 'RACE' | 'LOCATION' | 'ITEM' | null {
  if (field === 'race') return 'RACE'
  if (field === 'locationType') return 'LOCATION'
  if (field === 'itemType') return 'ITEM'
  return null
}

function iconValue(field: string): string {
  const object = props.object as unknown as Record<string, unknown>
  if (field === 'type') {
    return object.objectType === 'HERO' ? 'HERO' : object.objectType === 'CHARACTER' ? 'CHARACTER'
      : object.objectType === 'LOCATION' ? 'LOCATION' : 'ITEM'
  }
  if (field === 'race') return String(object.race ?? '')
  if (field === 'locationType') return String(object.locationType ?? '')
  if (field === 'itemType') return String(object.itemType ?? '')
  return ''
}

const blocks = computed(() => props.template.blocks ?? [])
</script>

<template>
  <div class="custom-card">
    <div
      v-for="block in blocks"
      :key="block.id ?? `${block.field}-${block.x}-${block.y}`"
      class="custom-card__block"
      :style="{
        left: `${block.x}px`,
        top: `${block.y}px`,
        width: `${block.width}px`,
        height: `${block.height}px`,
        fontSize: `${block.fontSize}px`,
        color: block.color || undefined,
        zIndex: block.zIndex ?? 0,
      }"
    >
      <template v-if="block.field === 'text' || block.text">{{ block.text }}</template>
      <template v-else-if="block.field === 'avatar'">
        <EntityImage v-if="object.avatarFileId" class="custom-card__avatar" :file-id="object.avatarFileId" alt="" />
      </template>
      <template v-else-if="block.display === 'ICON'">
        <svg
          v-if="iconKind(block.field)"
          class="custom-card__icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path :d="typeIconPath(iconKind(block.field)!, iconValue(block.field))" />
        </svg>
        <span
          v-else-if="block.field === 'klass' && classIconUrl(String((object as any).klass ?? ''))"
          class="custom-card__mask"
          :style="{ maskImage: `url(${classIconUrl(String((object as any).klass ?? ''))})`, WebkitMaskImage: `url(${classIconUrl(String((object as any).klass ?? ''))})` }"
        />
        <span v-else-if="block.field === 'gender'" class="custom-card__icon-text">
          {{ (object as any).gender === 'male' ? '♂' : (object as any).gender === 'female' ? '♀' : '' }}
        </span>
        <span v-else-if="block.field === 'alive'" class="custom-card__icon-text">
          {{ (object as any).alive === true ? '♥' : (object as any).alive === false ? '☠' : '?' }}
        </span>
        <span v-else-if="block.field === 'type'" class="custom-card__icon-text">◆</span>
      </template>
      <template v-else>{{ value(block.field) }}</template>
    </div>
  </div>
</template>

<style scoped>
.custom-card { position: relative; min-height: 96px; overflow: hidden; }
.custom-card__block {
  position: absolute; display: flex; align-items: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.custom-card__avatar { width: 100%; height: 100%; object-fit: cover; border-radius: 8px; }
.custom-card__icon { width: 100%; height: 100%; max-width: 28px; max-height: 28px; }
.custom-card__icon-text { font-size: 1.25em; line-height: 1; }
.custom-card__mask {
  width: 100%; height: 100%; max-width: 24px; max-height: 24px;
  background: currentColor;
  -webkit-mask: center / contain no-repeat;
  mask: center / contain no-repeat;
}
</style>
