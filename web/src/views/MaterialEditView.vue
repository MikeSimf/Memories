<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { errorMessage, fetchFileBlob, uploadFile } from '@/api'
import EntityImage from '@/components/EntityImage.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import type { AttachmentDto } from '@/api/types'

/** Редактор фото-материала: название, описание, картинки и документы. */
const route = useRoute()
const router = useRouter()
const campaign = useCampaignStore()
const ui = useUiStore()

const campaignId = computed(() => String(route.params.campaignId ?? ''))
const materialId = computed(() =>
  typeof route.params.materialId === 'string' ? route.params.materialId : '',
)
const existing = computed(() =>
  materialId.value ? campaign.materials.find((item) => item.id === materialId.value) : null,
)

const name = ref('')
const description = ref('')
const files = ref<AttachmentDto[]>([])
const saving = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)
/** Какие документы уже сохранены на устройстве (отметка «скачано»). */
const downloaded = ref<Set<string>>(new Set())

const STORAGE_KEY = 'dnd-material-downloads'

function loadDownloaded(): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    downloaded.value = new Set(raw ? (JSON.parse(raw) as string[]) : [])
  } catch {
    downloaded.value = new Set()
  }
}

function markDownloaded(id: string): void {
  const next = new Set(downloaded.value)
  next.add(id)
  downloaded.value = next
  localStorage.setItem(STORAGE_KEY, JSON.stringify([...next]))
}

function reset(): void {
  const material = existing.value
  name.value = material?.name ?? ''
  description.value = material?.description ?? ''
  files.value = material ? [...material.files] : []
}

watch(existing, reset, { immediate: true })
loadDownloaded()

function isImage(file: AttachmentDto): boolean {
  return (file.contentType ?? '').startsWith('image/')
}

async function pick(target: HTMLInputElement): Promise<void> {
  target.click()
}

async function onPicked(event: Event, imagesOnly: boolean): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  error.value = null
  try {
    const uploaded = await uploadFile(file)
    files.value = [
      ...files.value,
      {
        id: uploaded.id,
        name: file.name,
        contentType: uploaded.contentType ?? file.type,
        size: uploaded.size ?? file.size,
      },
    ]
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    uploading.value = false
    void imagesOnly
  }
}

function removeFile(id: string): void {
  files.value = files.value.filter((file) => file.id !== id)
}

/** Документ открывается скачиванием: сервер отдаёт файл по авторизованному запросу. */
async function downloadDocument(file: AttachmentDto): Promise<void> {
  try {
    const blob = await fetchFileBlob(file.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.name ?? 'document'
    link.click()
    URL.revokeObjectURL(url)
    markDownloaded(file.id)
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function save(): Promise<void> {
  if (name.value.trim() === '') {
    error.value = 'Укажите название материала'
    return
  }
  saving.value = true
  error.value = null
  try {
    const values = { name: name.value, description: description.value, files: files.value }
    if (materialId.value) {
      await campaign.updateMaterial(materialId.value, values)
    } else {
      await campaign.createMaterial(values)
    }
    ui.pushToast('success', 'Материал сохранён')
    router.push({ name: 'campaign-materials', params: { campaignId: campaignId.value } })
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  if (!materialId.value) return
  if (!window.confirm('Удалить материал?')) return
  await campaign.removeMaterial(materialId.value)
  ui.pushToast('success', 'Материал удалён')
  router.push({ name: 'campaign-materials', params: { campaignId: campaignId.value } })
}
</script>

<template>
  <section class="stack">
    <div class="row row--between">
      <h2>{{ materialId ? 'Материал' : 'Новый материал' }}</h2>
      <RouterLink class="btn btn--ghost" :to="{ name: 'campaign-materials', params: { campaignId } }">
        Назад
      </RouterLink>
    </div>

    <div v-if="error" class="notice notice--danger">{{ error }}</div>

    <div class="field">
      <label class="field__label" for="material-name">Название</label>
      <input id="material-name" v-model="name" class="input" type="text" />
    </div>

    <div class="field">
      <label class="field__label" for="material-description">Описание</label>
      <textarea id="material-description" v-model="description" class="textarea textarea--tall" />
    </div>

    <div class="field">
      <div class="row">
        <button class="btn btn--ghost btn--sm" type="button" :disabled="uploading" @click="pick(($refs.imageInput as HTMLInputElement))">
          Добавить фото
        </button>
        <button class="btn btn--ghost btn--sm" type="button" :disabled="uploading" @click="pick(($refs.docInput as HTMLInputElement))">
          Добавить документ
        </button>
        <span v-if="uploading" class="faint">Загружаю…</span>
      </div>
      <input ref="imageInput" type="file" accept="image/*" hidden @change="onPicked($event, true)" />
      <input ref="docInput" type="file" accept=".pdf,.doc,.docx,application/pdf" hidden @change="onPicked($event, false)" />

      <ul class="attachments">
        <li v-for="file in files" :key="file.id" class="attachments__row">
          <EntityImage
            v-if="isImage(file)"
            :file-id="file.id"
            :alt="file.name ?? ''"
            :max-height="80"
          />
          <span class="attachments__name">{{ file.name || file.id }}</span>
          <button
            v-if="!isImage(file)"
            class="btn btn--ghost btn--sm"
            type="button"
            @click="downloadDocument(file)"
          >
            {{ downloaded.has(file.id) ? 'скачано' : 'Скачать' }}
          </button>
          <button class="btn btn--ghost btn--sm" type="button" @click="removeFile(file.id)">
            Убрать
          </button>
        </li>
      </ul>
    </div>

    <div class="row row--end">
      <button v-if="materialId" class="btn btn--danger" type="button" @click="remove">Удалить</button>
      <button class="btn btn--primary" type="button" :disabled="saving" @click="save">Сохранить</button>
    </div>
  </section>
</template>

<style scoped>
.attachments {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
}
.attachments__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0;
  border-bottom: 1px solid var(--border);
}
.attachments__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
