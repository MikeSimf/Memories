<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  errorMessage,
  fetchFileForDownload,
  materialsApi,
  uploadFile,
  type AttachmentDto,
  type MaterialDto,
} from '@/api'
import EntityImage from '@/components/EntityImage.vue'
import { useUiStore } from '@/stores/ui'

/**
 * Редактор материала: название, описание, картинки и документы.
 * Сайт работает онлайн: правки сохраняются сразу на сервер, локальной копии нет.
 */
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const campaignId = computed(() => String(route.params.campaignId ?? ''))
const materialId = computed(() =>
  typeof route.params.materialId === 'string' ? route.params.materialId : '',
)

const name = ref('')
const description = ref('')
const files = ref<AttachmentDto[]>([])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)
/** Какие документы уже скачаны — состояние устройства, на сервер не уходит. */
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

/** Тело запроса: только поля материала, без служебных данных сервера. */
function body(): Partial<MaterialDto> {
  return {
    name: name.value.trim(),
    description: description.value,
    files: files.value.map((file) => ({
      id: file.id,
      name: file.name ?? '',
      contentType: file.contentType ?? '',
      size: file.size ?? 0,
    })),
  }
}

async function load(): Promise<void> {
  if (!materialId.value) {
    return
  }
  loading.value = true
  error.value = null
  try {
    const material = await materialsApi.get(campaignId.value, materialId.value)
    name.value = material.name ?? ''
    description.value = material.description ?? ''
    files.value = material.files ?? []
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDownloaded()
  void load()
})

function isImage(file: AttachmentDto): boolean {
  return (file.contentType ?? '').startsWith('image/')
}

function pick(target: HTMLInputElement): void {
  target.click()
}

async function onPicked(event: Event): Promise<void> {
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
  }
}

function removeFile(id: string): void {
  files.value = files.value.filter((file) => file.id !== id)
}

async function downloadDocument(file: AttachmentDto): Promise<void> {
  try {
    // Имя готовит сервер: транслит названия материала плюс расширение файла.
    const { blob, filename } = await fetchFileForDownload(file.id, name.value || file.name || undefined)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename ?? file.name ?? 'document'
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
    if (materialId.value) {
      await materialsApi.update(campaignId.value, materialId.value, body())
    } else {
      await materialsApi.create(campaignId.value, body())
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
  saving.value = true
  try {
    await materialsApi.remove(campaignId.value, materialId.value)
    ui.pushToast('success', 'Материал удалён')
    router.push({ name: 'campaign-materials', params: { campaignId: campaignId.value } })
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    saving.value = false
  }
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
    <p v-if="loading" class="faint">Загружаю…</p>

    <template v-else>
      <div class="field">
        <label class="field__label" for="material-name">Название</label>
        <input id="material-name" v-model="name" class="input" type="text" />
      </div>

      <div class="field">
        <label class="field__label" for="material-description">Описание</label>
        <textarea id="material-description" v-model="description" class="textarea textarea--tall" />
        <span class="field__hint">
          Поддерживается markdown и ссылки на объекты: <code>[[Имя]]</code>.
        </span>
      </div>

      <div class="field">
        <div class="row">
          <button
            class="btn btn--ghost btn--sm"
            type="button"
            :disabled="uploading"
            @click="pick($refs.imageInput as HTMLInputElement)"
          >
            Добавить фото
          </button>
          <button
            class="btn btn--ghost btn--sm"
            type="button"
            :disabled="uploading"
            @click="pick($refs.docInput as HTMLInputElement)"
          >
            Добавить документ
          </button>
          <span v-if="uploading" class="faint">Загружаю…</span>
        </div>
        <input ref="imageInput" type="file" accept="image/*" hidden @change="onPicked" />
        <input
          ref="docInput"
          type="file"
          accept=".pdf,.doc,.docx,application/pdf"
          hidden
          @change="onPicked"
        />

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
        <button v-if="materialId" class="btn btn--danger" type="button" :disabled="saving" @click="remove">
          Удалить
        </button>
        <button class="btn btn--primary" type="button" :disabled="saving" @click="save">
          Сохранить
        </button>
      </div>
    </template>
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
