<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { classesApi, errorMessage, racesApi, uploadFile } from '@/api'
import { OBJECT_CLASSES, OBJECT_CLASS_LABELS, type ObjectClass } from '@/api/types'
import EntityImage from './EntityImage.vue'
import MarkupText from './MarkupText.vue'
import { useCampaignStore } from '@/stores/campaign'
import { useUiStore } from '@/stores/ui'
import { emptyHeroItem, type HeroItemFormValue, type ObjectFormValues } from '@/types/forms'

const props = withDefaults(
  defineProps<{
    modelValue: ObjectFormValues
    /** Класс объекта: при правке не меняется, при создании выбирается. */
    objectClass: ObjectClass
    mode?: 'create' | 'edit'
    saving?: boolean
    submitLabel?: string
    /** Разрешить выбор класса (при создании). */
    allowClassChange?: boolean
    /** Только чтение для тех, кому герой недоступен. */
    disabled?: boolean
  }>(),
  { mode: 'create', saving: false, submitLabel: 'Сохранить', allowClassChange: true, disabled: false },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: ObjectFormValues): void
  (event: 'update:objectClass', value: ObjectClass): void
  (event: 'submit'): void
  (event: 'cancel'): void
}>()

const campaign = useCampaignStore()
const ui = useUiStore()

/** Справочник рас: подсказки в поле «Раса», свободный ввод не запрещён. */
const races = ref<string[]>([])
const classes = ref<string[]>([])

onMounted(async () => {
  try {
    races.value = (await racesApi.list()).map((race) => race.name)
    classes.value = (await classesApi.list()).map((item) => item.name)
  } catch {
    // Без сети остаётся обычный ввод.
  }
})

const showPreview = ref(false)
const uploading = ref(false)

/** Подсказки для ввода [[Имя]]: имена объектов кампании. */
const wikiHints = ref<string[]>([])
const descriptionEl = ref<HTMLTextAreaElement | null>(null)

function objectNames(): string[] {
  const names = new Set<string>()
  for (const item of campaign.objects) {
    if (item.name && item.name.trim() !== "") names.add(item.name)
  }
  return [...names].sort((a, b) => a.localeCompare(b, "ru"))
}

/** Что набрано после ближайшего «[[» до курсора. */
function wikiQuery(el: HTMLTextAreaElement): string | null {
  const caret = el.selectionStart ?? 0
  const before = el.value.slice(0, caret)
  const open = before.lastIndexOf("[[")
  if (open < 0) return null
  const inside = before.slice(open + 2)
  if (inside.includes("]]")) return null
  return inside
}

function refreshHints(): void {
  const el = descriptionEl.value
  if (!el) return
  const query = wikiQuery(el)
  if (query === null || query.trim().length < 3) {
    wikiHints.value = []
    return
  }
  const needle = query.trim().toLowerCase()
  wikiHints.value = objectNames()
    .filter((name) => name.toLowerCase().includes(needle))
    .slice(0, 6)
}

/** Ввод «[[» сразу закрывается: курсор встаёт между скобками. */
function onDescriptionInput(event: Event): void {
  const el = event.target as HTMLTextAreaElement
  const caret = el.selectionStart ?? 0
  const typed = el.value.slice(Math.max(0, caret - 2), caret)
  if (typed === "[[" && el.value.slice(caret, caret + 2) !== "]]") {
    const next = el.value.slice(0, caret) + "]]" + el.value.slice(caret)
    patch({ description: next })
    el.value = next
    el.setSelectionRange(caret, caret)
  } else {
    patch({ description: el.value })
  }
  refreshHints()
}

/** Подстановка выбранного имени: закрывающие скобки добавляются сами. */
function applyHint(name: string): void {
  const el = descriptionEl.value
  if (!el) return
  const caret = el.selectionStart ?? 0
  const before = el.value.slice(0, caret)
  const open = before.lastIndexOf("[[")
  if (open < 0) return
  const head = el.value.slice(0, open + 2)
  const tail = el.value.slice(caret)
  const closing = tail.startsWith("]]") ? "" : "]]"
  const next = head + name + closing + tail
  patch({ description: next })
  el.value = next
  const pos = head.length + name.length + closing.length
  el.focus()
  el.setSelectionRange(pos, pos)
  wikiHints.value = []
}

const classLocked = computed(() => props.mode === 'edit' || !props.allowClassChange)
const isHero = computed(() => props.objectClass === 'HERO')
const isCharacter = computed(() => props.objectClass === 'CHARACTER')
const isItem = computed(() => props.objectClass === 'ITEM')

/** Игрок героя — участник кампании (`playerUserId`). */
const playerOptions = computed(() =>
  campaign.members
    .filter((member) => member.status !== 'REMOVED')
    .map((member) => ({ id: member.userId, name: member.displayName })),
)

function patch(changes: Partial<ObjectFormValues>): void {
  emit('update:modelValue', { ...props.modelValue, ...changes })
}

function submit(): void {
  emit('submit')
}

/* ------------------------------ Котомка героя ----------------------------- */

function updateItem(index: number, changes: Partial<HeroItemFormValue>): void {
  const items = props.modelValue.items.map((item, position) =>
    position === index ? { ...item, ...changes } : item,
  )
  patch({ items })
}

function addItem(): void {
  patch({ items: [...props.modelValue.items, emptyHeroItem()] })
}

function removeItem(index: number): void {
  patch({ items: props.modelValue.items.filter((_, position) => position !== index) })
}

/* --------------------------- Ссылки предмета ------------------------------ */

/** Ссылка не более чем на одного владельца: выбор одного очищает другой. */
function setOwnerHero(value: string): void {
  patch({ ownerHeroId: value === '' ? null : value, ownerCharacterId: null })
}

function setOwnerCharacter(value: string): void {
  patch({ ownerCharacterId: value === '' ? null : value, ownerHeroId: null })
}

/** Загрузка изображения — только при наличии сети (`architecture.md` §7). */
async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const uploaded = await uploadFile(file)
    patch({ imageFileId: uploaded.id })
    ui.pushToast('success', 'Изображение загружено')
  } catch (caught) {
    ui.pushToast('error', errorMessage(caught))
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <form class="stack" @submit.prevent="submit">
    <div class="row" style="align-items: flex-end">
      <div class="field" style="flex: 0 0 190px">
        <label class="field__label" for="object-class">Класс объекта</label>
        <select
          id="object-class"
          class="select"
          :value="objectClass"
          :disabled="classLocked"
          @change="emit('update:objectClass', ($event.target as HTMLSelectElement).value as ObjectClass)"
        >
          <option v-for="cls in OBJECT_CLASSES" :key="cls" :value="cls">
            {{ OBJECT_CLASS_LABELS[cls] }}
          </option>
        </select>
      </div>

      <div class="field" style="flex: 1; min-width: 220px">
        <label class="field__label" for="object-name">Название</label>
        <input
          id="object-name"
          class="input"
          type="text"
          required
          :value="modelValue.name"
          placeholder="Силдар Холлвинтер"
          @input="patch({ name: ($event.target as HTMLInputElement).value })"
        />
      </div>
    </div>

    <!-- Герой: раса, класс, игрок -->
    <template v-if="isHero">
      <div class="row" style="align-items: flex-end">
        <div class="field" style="flex: 1; min-width: 180px">
          <label class="field__label" for="hero-race">Раса</label>
          <select
            id="hero-race"
            class="input"
            :value="modelValue.race ?? 'Неизвестно'"
            @change="patch({ race: ($event.target as HTMLSelectElement).value })"
          >
            <option v-for="race in races" :key="race" :value="race">{{ race }}</option>
          </select>
        </div>
        <div class="field" style="flex: 1; min-width: 180px">
          <label class="field__label" for="hero-klass">Класс</label>
          <select
            id="hero-klass"
            class="input"
            :value="modelValue.klass ?? 'Неизвестно'"
            @change="patch({ klass: ($event.target as HTMLSelectElement).value })"
          >
            <option v-for="item in classes" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="hero-player">Игрок</label>
          <select
            id="hero-player"
            class="select"
            :value="modelValue.playerUserId ?? ''"
            @change="patch({ playerUserId: ($event.target as HTMLSelectElement).value || null })"
          >
            <option value="">не указан</option>
            <option v-for="player in playerOptions" :key="player.id" :value="player.id">
              {{ player.name }}
            </option>
          </select>
        </div>
      </div>

      <div class="field">
        <div class="row row--between">
          <span class="field__label">Предметы в котомке</span>
          <button class="btn btn--ghost btn--sm" type="button" @click="addItem">Добавить предмет</button>
        </div>
        <p v-if="modelValue.items.length === 0" class="faint">
          Список пуст. Предмет можно связать с предметом кампании — тогда откроется его карточка.
        </p>
        <div v-else class="stack stack--sm">
          <div v-for="(item, index) in modelValue.items" :key="index" class="row" style="align-items: flex-end">
            <div class="field" style="flex: 2; min-width: 180px">
              <label class="field__label" :for="`hero-item-name-${index}`">Название</label>
              <input
                :id="`hero-item-name-${index}`"
                class="input"
                type="text"
                :value="item.name"
                placeholder="Меч справедливости"
                @input="updateItem(index, { name: ($event.target as HTMLInputElement).value })"
              />
            </div>
            <div class="field" style="flex: 0 0 110px">
              <label class="field__label" :for="`hero-item-qty-${index}`">Кол-во</label>
              <input
                :id="`hero-item-qty-${index}`"
                class="input"
                type="number"
                min="0"
                :value="item.quantity ?? ''"
                @input="
                  updateItem(index, {
                    quantity:
                      ($event.target as HTMLInputElement).value === ''
                        ? null
                        : Number(($event.target as HTMLInputElement).value),
                  })
                "
              />
            </div>
            <div class="field" style="flex: 2; min-width: 180px">
              <label class="field__label" :for="`hero-item-ref-${index}`">Предмет кампании</label>
              <select
                :id="`hero-item-ref-${index}`"
                class="select"
                :value="item.itemId ?? ''"
                @change="updateItem(index, { itemId: ($event.target as HTMLSelectElement).value || null })"
              >
                <option value="">по названию</option>
                <option v-for="option in campaign.items" :key="option.id" :value="option.id">
                  {{ option.name }}
                </option>
              </select>
            </div>
            <div class="field" style="flex: 2; min-width: 180px">
              <label class="field__label" :for="`hero-item-notes-${index}`">Заметки</label>
              <input
                :id="`hero-item-notes-${index}`"
                class="input"
                type="text"
                :value="item.notes"
                placeholder="подарок мэра"
                @input="updateItem(index, { notes: ($event.target as HTMLInputElement).value })"
              />
            </div>
            <button class="btn btn--ghost btn--sm" type="button" @click="removeItem(index)">Убрать</button>
          </div>
        </div>
      </div>
    </template>

    <!-- Персонаж (НПС): раса и краткое описание -->
    <template v-if="isCharacter">
      <div class="row" style="align-items: flex-end">
        <div class="field" style="flex: 1; min-width: 180px">
          <label class="field__label" for="character-race">Раса</label>
          <select
            id="character-race"
            class="input"
            :value="modelValue.race ?? 'Неизвестно'"
            @change="patch({ race: ($event.target as HTMLSelectElement).value })"
          >
            <option v-for="race in races" :key="race" :value="race">{{ race }}</option>
          </select>
        </div>
        <div class="field" style="flex: 2; min-width: 220px">
          <label class="field__label" for="character-short">Краткое описание</label>
          <input
            id="character-short"
            class="input"
            type="text"
            :value="modelValue.shortDescription"
            placeholder="Мэр Фандалина"
            @input="patch({ shortDescription: ($event.target as HTMLInputElement).value })"
          />
        </div>
      </div>
      <span class="field__hint">По расе и краткому описанию тоже идёт поиск.</span>
    </template>

    <!-- Предмет: владелец строкой и необязательная ссылка -->
    <template v-if="isItem">
      <div class="field">
        <label class="field__label" for="item-owner">Владелец</label>
        <input
          id="item-owner"
          class="input"
          type="text"
          :value="modelValue.ownerName"
          placeholder="гильдия воров"
          @input="patch({ ownerName: ($event.target as HTMLInputElement).value })"
        />
        <span class="field__hint">Как написано в мире. Ссылку можно указать ниже — либо на героя, либо на персонажа.</span>
      </div>
      <div class="row" style="align-items: flex-end">
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="item-owner-hero">Владелец-герой</label>
          <select
            id="item-owner-hero"
            class="select"
            :value="modelValue.ownerHeroId ?? ''"
            @change="setOwnerHero(($event.target as HTMLSelectElement).value)"
          >
            <option value="">не указан</option>
            <option v-for="hero in campaign.heroes" :key="hero.id" :value="hero.id">
              {{ hero.name }}
            </option>
          </select>
        </div>
        <div class="field" style="flex: 1; min-width: 200px">
          <label class="field__label" for="item-owner-character">Владелец-персонаж</label>
          <select
            id="item-owner-character"
            class="select"
            :value="modelValue.ownerCharacterId ?? ''"
            @change="setOwnerCharacter(($event.target as HTMLSelectElement).value)"
          >
            <option value="">не указан</option>
            <option v-for="character in campaign.characters" :key="character.id" :value="character.id">
              {{ character.name }}
            </option>
          </select>
        </div>
      </div>
    </template>

    <div class="field">
      <span class="field__label">Изображение</span>
      <div v-if="modelValue.imageFileId" class="stack stack--sm">
        <EntityImage :file-id="modelValue.imageFileId" :alt="modelValue.name || 'Изображение'" />
        <div class="row">
          <button class="btn btn--sm" type="button" @click="patch({ imageFileId: null })">
            Убрать изображение
          </button>
        </div>
      </div>
      <input type="file" accept="image/*" :disabled="uploading" @change="onFileSelected" />
      <span class="field__hint">
        Файл до 10 МБ, загружается на сервер сразу — в офлайне прикрепление недоступно.
      </span>
    </div>

    <div class="field">
      <div class="row row--between">
        <label class="field__label" for="object-description">Описание</label>
        <button class="btn btn--ghost btn--sm" type="button" @click="showPreview = !showPreview">
          {{ showPreview ? 'Скрыть предпросмотр' : 'Предпросмотр разметки' }}
        </button>
      </div>
      <textarea
        id="object-description"
        ref="descriptionEl"
        class="textarea textarea--tall"
        :value="modelValue.description"
        placeholder="Связан с [[Лира Ксенофонт|Лиры]] долгом…"
        @input="onDescriptionInput"
        @keyup="refreshHints"
        @click="refreshHints"
      />
      <ul v-if="wikiHints.length > 0" class="wiki-hints">
        <li v-for="hint in wikiHints" :key="hint">
          <button type="button" @click="applyHint(hint)">{{ hint }}</button>
        </li>
      </ul>
      <span class="field__hint">
        Поддерживается markdown и ссылки на объекты: <code>[[Имя]]</code>,
        <code>[[Имя|подпись]]</code>, <code>[[#uuid|подпись]]</code>.
      </span>
      <div v-if="showPreview" class="preview-box">
        <MarkupText v-if="modelValue.description.trim()" :text="modelValue.description" />
        <span v-else class="faint">Пусто</span>
      </div>
    </div>

    <datalist id="race-options">
      <option v-for="race in races" :key="race" :value="race" />
    </datalist>

    <div class="row row--end">
      <button class="btn" type="button" :disabled="saving" @click="emit('cancel')">Отмена</button>
      <button
        class="btn btn--primary"
        type="submit"
        :disabled="saving || disabled || modelValue.name.trim() === ''"
      >
        <span v-if="saving" class="spinner" aria-hidden="true" />
        {{ submitLabel }}
      </button>
    </div>
  </form>
</template>
