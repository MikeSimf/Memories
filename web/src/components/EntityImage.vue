<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import { fetchFileBlob } from '@/api'

/**
 * Изображение объекта: `GET /api/files/{id}` требует авторизации, поэтому файл
 * скачивается с токеном и показывается через object URL.
 */
const props = withDefaults(
  defineProps<{ fileId: string | null; alt?: string; maxHeight?: number }>(),
  { alt: 'Изображение объекта', maxHeight: 280 },
)

const objectUrl = ref<string | null>(null)
const failed = ref(false)
/** Полноэкранный просмотр: картинка не растягивается, пропорции сохраняются. */
const zoom = ref(false)

function revoke(): void {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = null
  }
}

async function load(): Promise<void> {
  revoke()
  failed.value = false
  if (!props.fileId) return
  try {
    const blob = await fetchFileBlob(props.fileId)
    objectUrl.value = URL.createObjectURL(blob)
  } catch {
    failed.value = true
  }
}

watch(() => props.fileId, () => void load(), { immediate: true })
onBeforeUnmount(revoke)
</script>

<template>
  <img
    v-if="objectUrl"
    class="entity-image"
    :src="objectUrl"
    :alt="alt"
    :style="{ maxHeight: `${maxHeight}px` }"
    @click="zoom = true"
  />
  <span v-else-if="failed" class="faint">Изображение недоступно без сети</span>
  <Teleport to="body">
    <div v-if="zoom && objectUrl" class="image-zoom" @click="zoom = false">
      <img :src="objectUrl" :alt="alt" />
      <button class="image-zoom__close" type="button" @click="zoom = false">Закрыть</button>
    </div>
  </Teleport>
</template>
