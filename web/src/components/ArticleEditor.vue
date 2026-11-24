<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

import { errorMessage, uploadFile } from '@/api'

/** Редактор статьи: абзацы, шрифт, красная строка и картинки между абзацами. */
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const editor = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const error = ref<string | null>(null)
let savedRange: Range | null = null

const FONTS = [
  { value: '', label: 'Обычный' },
  { value: 'Georgia, serif', label: 'С засечками' },
  { value: 'Arial, sans-serif', label: 'Без засечек' },
  { value: 'Courier New, monospace', label: 'Моноширинный' },
]
const SIZES = [
  { value: '', label: 'Обычный' },
  { value: '14px', label: 'Мелкий' },
  { value: '18px', label: 'Крупный' },
  { value: '22px', label: 'Заголовок' },
]

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function toHtml(value: string): string {
  if (/<(p|div|h[1-6]|figure|img|blockquote|ul|ol)\b/i.test(value)) return value
  return value
    .split(/\n{2,}/)
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`)
    .join('')
}

function sync(): void {
  if (editor.value) emit('update:modelValue', editor.value.innerHTML)
}

function rememberSelection(): void {
  const selection = window.getSelection()
  if (selection && selection.rangeCount > 0) savedRange = selection.getRangeAt(0).cloneRange()
}

function restoreSelection(): void {
  if (!savedRange) return
  const selection = window.getSelection()
  selection?.removeAllRanges()
  selection?.addRange(savedRange)
}

function blockAtSelection(): HTMLElement | null {
  const selection = window.getSelection()
  let node: Node | null = selection?.anchorNode ?? null
  while (node && node !== editor.value) {
    if (node instanceof HTMLElement && /^(P|DIV|H1|H2|H3|BLOCKQUOTE|LI|FIGCAPTION)$/.test(node.tagName)) return node
    node = node.parentNode
  }
  return editor.value
}

function applyStyle(property: 'fontFamily' | 'fontSize' | 'textIndent', value: string): void {
  const block = blockAtSelection()
  if (!block) return
  if (property === 'textIndent' && block.style.textIndent) block.style.textIndent = ''
  else block.style[property] = value
  sync()
}

function command(name: string): void {
  restoreSelection()
  document.execCommand(name, false)
  sync()
}

function openImageDialog(): void {
  rememberSelection()
  fileInput.value?.click()
}

async function onImageChosen(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true
  error.value = null
  try {
    const uploaded = await uploadFile(file)
    const src = `/api/files/${uploaded.id}`
    restoreSelection()
    document.execCommand(
      'insertHTML',
      false,
      `<figure contenteditable="false"><img src="${src}" alt=""><figcaption>Подпись к картинке</figcaption></figure><p><br></p>`,
    )
    sync()
  } catch (caught) {
    error.value = errorMessage(caught)
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

onMounted(() => {
  if (editor.value) editor.value.innerHTML = toHtml(props.modelValue)
})

watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value || document.activeElement === editor.value) return
    if (editor.value.innerHTML !== value) editor.value.innerHTML = toHtml(value)
  },
)
</script>

<template>
  <div class="article-editor">
    <div class="article-editor__toolbar" role="toolbar" aria-label="Форматирование статьи">
      <select class="select select--sm" aria-label="Шрифт" @change="applyStyle('fontFamily', ($event.target as HTMLSelectElement).value)">
        <option v-for="font in FONTS" :key="font.value" :value="font.value">{{ font.label }}</option>
      </select>
      <select class="select select--sm" aria-label="Размер" @change="applyStyle('fontSize', ($event.target as HTMLSelectElement).value)">
        <option v-for="size in SIZES" :key="size.value" :value="size.value">{{ size.label }}</option>
      </select>
      <button class="article-editor__button" type="button" title="Полужирный" aria-label="Полужирный" @mousedown.prevent="command('bold')"><b>Ж</b></button>
      <button class="article-editor__button" type="button" title="Курсив" aria-label="Курсив" @mousedown.prevent="command('italic')"><i>К</i></button>
      <button class="article-editor__button" type="button" title="Красная строка" aria-label="Красная строка" @mousedown.prevent="applyStyle('textIndent', '2em')">¶</button>
      <button class="article-editor__button" type="button" title="Заголовок" aria-label="Заголовок" @mousedown.prevent="document.execCommand('formatBlock', false, 'h2'); sync()">H2</button>
      <button class="article-editor__button" type="button" title="Цитата" aria-label="Цитата" @mousedown.prevent="document.execCommand('formatBlock', false, 'blockquote'); sync()">❞</button>
      <button class="article-editor__button" type="button" :disabled="uploading" title="Вставить картинку" aria-label="Вставить картинку" @mousedown.prevent="openImageDialog">🖼</button>
      <input ref="fileInput" class="visually-hidden" type="file" accept="image/*" @change="onImageChosen" />
      <span v-if="uploading" class="faint">Загружаем…</span>
    </div>
    <div
      ref="editor"
      class="article-editor__body"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      spellcheck="true"
      @input="sync"
      @keyup="rememberSelection"
      @mouseup="rememberSelection"
    />
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <p class="field__hint">
      Выделите абзац и выберите шрифт, размер или «¶» для красной строки. Картинка вставляется в место курсора.
    </p>
  </div>
</template>

<style scoped>
.article-editor { border: 1px solid var(--border); border-radius: 12px; overflow: hidden; background: var(--surface); }
.article-editor__toolbar { display: flex; flex-wrap: wrap; gap: .3rem; align-items: center; padding: .45rem; border-bottom: 1px solid var(--border); background: var(--surface-2, rgba(0,0,0,.03)); }
.article-editor__button { min-width: 34px; height: 34px; border: 1px solid var(--border); border-radius: 7px; background: var(--surface); color: var(--text); cursor: pointer; }
.article-editor__button:hover { border-color: var(--accent); }
.select--sm { height: 34px; padding: 0 .4rem; }
.article-editor__body { min-height: 340px; padding: 1.1rem 1.2rem; outline: none; line-height: 1.65; }
.article-editor__body :deep(p) { margin: 0 0 .85rem; }
.article-editor__body :deep(figure) { margin: 1rem 0; }
.article-editor__body :deep(img) { max-width: 100%; height: auto; border-radius: 10px; }
.article-editor__body :deep(figcaption) { color: var(--text-muted); font-size: .85rem; padding-top: .35rem; }
.article-editor__body :deep(blockquote) { margin: 1rem 0; padding: .5rem 1rem; border-left: 3px solid var(--accent); color: var(--text-muted); }
</style>
