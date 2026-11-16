<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage, questsApi, uploadFile } from '@/api'
import EntityImage from '@/components/EntityImage.vue'

const route = useRoute()
const router = useRouter()
const campaignId = String(route.params.campaignId ?? '')
const questId = computed(() => (typeof route.params.questId === 'string' ? route.params.questId : null))
const name = ref('')
const description = ref('')
const avatarFileId = ref<string | null>(null)
const error = ref<string | null>(null)
const saving = ref(false)

onMounted(async () => {
  if (!questId.value) return
  try {
    const quest = await questsApi.get(campaignId, questId.value)
    name.value = quest.name
    description.value = quest.description ?? ''
    avatarFileId.value = quest.avatarFileId
  } catch (caught) { error.value = errorMessage(caught) }
})

async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try { avatarFileId.value = (await uploadFile(file)).id } catch (caught) { error.value = errorMessage(caught) }
}

async function save(): Promise<void> {
  if (!name.value.trim()) return
  saving.value = true
  try {
    const body = { name: name.value.trim(), description: description.value, avatarFileId: avatarFileId.value }
    const saved = questId.value ? await questsApi.update(campaignId, questId.value, body) : await questsApi.create(campaignId, body)
    await router.push({ name: 'quest-detail', params: { campaignId, questId: saved.id } })
  } catch (caught) { error.value = errorMessage(caught) } finally { saving.value = false }
}
</script>

<template>
  <div class="stack">
    <h1>{{ questId ? 'Изменить квест' : 'Новый квест' }}</h1>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <form class="card stack" @submit.prevent="save">
      <div class="field"><label class="field__label">Название</label><input v-model="name" class="input" required /></div>
      <div class="field"><label class="field__label">Описание</label><textarea v-model="description" class="input" rows="6" /></div>
      <div class="field"><label class="field__label">Аватар</label><label class="btn btn--sm">Загрузить<input type="file" accept="image/*" hidden @change="upload" /></label><div v-if="avatarFileId" style="max-width: 160px; margin-top: 8px"><EntityImage :file-id="avatarFileId" alt="" /></div></div>
      <div class="row"><button class="btn btn--primary" :disabled="saving" type="submit">Сохранить</button><button class="btn" type="button" @click="router.back()">Отмена</button></div>
    </form>
  </div>
</template>
