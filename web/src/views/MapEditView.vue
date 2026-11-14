<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage, mapsApi, uploadFile } from '@/api'
import EntityImage from '@/components/EntityImage.vue'

const route = useRoute()
const router = useRouter()
const campaignId = String(route.params.campaignId ?? '')
const mapId = computed(() => (typeof route.params.mapId === 'string' ? route.params.mapId : null))
const name = ref('')
const description = ref('')
const imageFileId = ref<string | null>(null)
const saving = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  if (!mapId.value) return
  try {
    const map = await mapsApi.get(campaignId, mapId.value)
    name.value = map.name
    description.value = map.description ?? ''
    imageFileId.value = map.imageFileId
  } catch (caught) {
    error.value = errorMessage(caught)
  }
})

async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const uploaded = await uploadFile(file)
    imageFileId.value = uploaded.id
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function save(): Promise<void> {
  if (!name.value.trim()) return
  saving.value = true
  try {
    const body = { name: name.value.trim(), description: description.value, imageFileId: imageFileId.value }
    const saved = mapId.value
      ? await mapsApi.update(campaignId, mapId.value, body)
      : await mapsApi.create(campaignId, body)
    await router.push({ name: 'map-detail', params: { campaignId, mapId: saved.id } })
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>{{ mapId ? 'Изменить карту' : 'Новая карта' }}</h1>
      </div>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <form class="card stack" @submit.prevent="save">
      <div class="field">
        <label class="field__label">Название</label>
        <input v-model="name" class="input" required />
      </div>
      <div class="field">
        <label class="field__label">Описание</label>
        <textarea v-model="description" class="input" rows="3" />
      </div>
      <div class="field">
        <label class="field__label">Фоновая карта</label>
        <div class="row">
          <label class="btn btn--sm">Загрузить картинку<input type="file" accept="image/*" hidden @change="upload" /></label>
          <span v-if="imageFileId" class="faint">Картинка загружена</span>
        </div>
        <div v-if="imageFileId" class="map-preview">
          <EntityImage :file-id="imageFileId" alt="" />
        </div>
      </div>
      <div class="row">
        <button class="btn btn--primary" :disabled="saving" type="submit">Сохранить</button>
        <button class="btn" type="button" @click="router.back()">Отмена</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.map-preview { margin-top: 10px; max-height: 320px; overflow: hidden; border-radius: 10px; }
.map-preview img { width: 100%; height: 100%; object-fit: contain; }
</style>
