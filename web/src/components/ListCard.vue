<script setup lang="ts">
import { computed } from 'vue'

import type { CardTemplateDto } from '@/api'
import type { LocalObject } from '@/db/schema'
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
      }"
    >
      <template v-if="block.field === 'avatar'">
        <img v-if="object.avatarFileId" class="custom-card__avatar" :src="`/api/files/${object.avatarFileId}`" alt="" />
      </template>
      <template v-else>{{ value(block.field) }}</template>
    </div>
  </div>
</template>

<style scoped>
.custom-card {
  position: relative;
  min-height: 96px;
  overflow: hidden;
}
.custom-card__block {
  position: absolute;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.custom-card__avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}
</style>
