<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { cardsApi, errorMessage, type CardTemplateDto } from '@/api'
import AdminNav from '@/components/AdminNav.vue'
import { classIconUrl } from '@/ui/classIcons'
import { typeIconPath } from '@/ui/typeIcons'
import type { ObjectClass } from '@/api/types'

const templates = ref<CardTemplateDto[]>([])
const error = ref<string | null>(null)
const editing = ref<Partial<CardTemplateDto> | null>(null)
const fields = ['avatar', 'name', 'type', 'race', 'klass', 'gender', 'alive', 'shortDescription', 'itemType', 'locationType', 'magic', 'hasSetting', 'ownerName']
const classes: ObjectClass[] = ['CHARACTER', 'HERO', 'LOCATION', 'ITEM']
const selectedIndex = ref<number | null>(null)
const corners = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const
let drag: { index: number; mode: string; startX: number; startY: number; x: number; y: number; w: number; h: number } | null = null

function blank(): Partial<CardTemplateDto> {
  return {
    name: 'Стандартная',
    objectType: 'CHARACTER',
    kind: 'LIST',
    formula: 'true',
    priority: 0,
    isDefault: false,
    blocks: [
      { field: 'avatar', display: 'ICON', x: 8, y: 8, width: 48, height: 48, fontSize: 14 },
      { field: 'name', display: 'TEXT', x: 64, y: 10, width: 220, height: 26, fontSize: 16 },
      { field: 'race', display: 'TEXT', x: 64, y: 42, width: 180, height: 20, fontSize: 14 },
    ],
  }
}

function edit(template?: CardTemplateDto): void {
  editing.value = template ? JSON.parse(JSON.stringify(template)) : blank()
}

function addText(): void {
  if (!editing.value) return
  editing.value.blocks = [...(editing.value.blocks ?? []), {
    field: 'text', display: 'TEXT', text: 'Статус', x: 12, y: 90, width: 120, height: 22, fontSize: 14,
  }]
}

function addBlock(): void {
  if (!editing.value) return
  editing.value.blocks = [...(editing.value.blocks ?? []), { field: 'name', display: 'TEXT', x: 8, y: 8, width: 160, height: 24, fontSize: 14 }]
}

function selectAndDrag(index: number, event: PointerEvent, mode: string): void {
  selectedIndex.value = index
  startDrag(index, event, mode)
}

function startDrag(index: number, event: PointerEvent, mode: string): void {
  event.preventDefault()
  const block = editing.value?.blocks?.[index]
  if (!block) return
  drag = { index, mode, startX: event.clientX, startY: event.clientY, x: block.x, y: block.y, w: block.width, h: block.height };
  (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', stopDrag, { once: true })
}

function onDrag(event: PointerEvent): void {
  if (!drag || !editing.value?.blocks) return
  const block = editing.value.blocks[drag.index]
  if (!block) return
  const dx = event.clientX - drag.startX
  const dy = event.clientY - drag.startY
  if (drag.mode === 'move') {
    block.x = Math.max(0, Math.round(drag.x + dx))
    block.y = Math.max(0, Math.round(drag.y + dy))
    return
  }
  if (drag.mode.includes('e')) block.width = Math.max(20, Math.round(drag.w + dx))
  if (drag.mode.includes('s')) block.height = Math.max(12, Math.round(drag.h + dy))
  if (drag.mode.includes('w')) {
    const width = Math.max(20, Math.round(drag.w - dx))
    block.x = Math.max(0, Math.round(drag.x + (drag.w - width)))
    block.width = width
  }
  if (drag.mode.includes('n')) {
    const height = Math.max(12, Math.round(drag.h - dy))
    block.y = Math.max(0, Math.round(drag.y + (drag.h - height)))
    block.height = height
  }
}

function stopDrag(): void {
  drag = null
  window.removeEventListener('pointermove', onDrag)
}

function previewIconKind(field: string): 'RACE' | 'LOCATION' | 'ITEM' | null {
  if (field === 'race') return 'RACE'
  if (field === 'locationType') return 'LOCATION'
  if (field === 'itemType') return 'ITEM'
  return null
}

function previewIconValue(field: string): string {
  if (field === 'race') return 'Эльф'
  if (field === 'locationType') return 'Город'
  if (field === 'itemType') return 'Артефакт'
  return ''
}

function blockValue(field: string): string {
  return { name: 'Имя', type: 'Персонаж', race: 'Эльф', klass: 'Волшебник', gender: 'женский', alive: 'жив', itemType: 'Артефакт', magic: 'да', ownerName: 'Лира' }[field] ?? field
}

async function save(): Promise<void> {
  if (!editing.value) return
  try {
    await cardsApi.save(editing.value)
    editing.value = null
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function remove(template: CardTemplateDto): Promise<void> {
  if (!confirm(`Удалить карточку «${template.name}»?`)) return
  try {
    await cardsApi.remove(template.id)
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

function exportJson(): void {
  const blob = new Blob([JSON.stringify(templates.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'card-templates.json'
  link.click()
  URL.revokeObjectURL(url)
}

async function importJson(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const data = JSON.parse(await file.text()) as CardTemplateDto[]
    for (const item of data) {
      await cardsApi.save({ ...item, id: undefined })
    }
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

function duplicate(template: CardTemplateDto): void {
  editing.value = { ...JSON.parse(JSON.stringify(template)), id: undefined, name: `${template.name} (копия)` }
}

async function createDefaults(): Promise<void> {
  const block = (field: string, display: 'TEXT' | 'ICON', x: number, y: number, width: number, height: number, fontSize: number) =>
    ({ field, display, x, y, width, height, fontSize })
  const common = [
    block('avatar', 'ICON', 8, 8, 44, 44, 14),
    block('name', 'TEXT', 60, 4, 232, 26, 16),
  ]
  const variants: Array<[CardTemplateDto['objectType'], string, ReturnType<typeof block>[]]> = [
    ['CHARACTER', 'Персонаж', [
      ...common,
      block('race', 'TEXT', 60, 32, 180, 18, 13),
      block('klass', 'TEXT', 60, 52, 180, 18, 13),
      block('gender', 'ICON', 268, 4, 24, 22, 16),
      block('alive', 'ICON', 268, 28, 24, 22, 16),
      block('type', 'TEXT', 8, 74, 120, 18, 12),
    ]],
    ['HERO', 'Герой', [
      ...common,
      block('race', 'TEXT', 60, 32, 180, 18, 13),
      block('klass', 'TEXT', 60, 52, 180, 18, 13),
      block('gender', 'ICON', 268, 4, 24, 22, 16),
      block('alive', 'ICON', 268, 28, 24, 22, 16),
      block('type', 'TEXT', 8, 74, 120, 18, 12),
    ]],
    ['LOCATION', 'Локация', [
      ...common,
      block('locationType', 'TEXT', 60, 32, 220, 18, 13),
      block('type', 'TEXT', 8, 74, 120, 18, 12),
    ]],
    ['ITEM', 'Предмет', [
      ...common,
      block('itemType', 'TEXT', 60, 32, 220, 18, 13),
      block('ownerName', 'TEXT', 60, 52, 220, 18, 13),
      block('magic', 'ICON', 268, 4, 24, 22, 16),
      block('type', 'TEXT', 8, 74, 120, 18, 12),
    ]],
  ]
  for (const [objectType, label, blocks] of variants) {
    await cardsApi.save({
      name: `Стандартная: ${label}`,
      objectType,
      kind: 'LIST',
      formula: 'true',
      priority: 0,
      isDefault: true,
      blocks,
    })
  }
  await load()
}

async function load(): Promise<void> {
  try {
    templates.value = await cardsApi.list()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Карточки списков</h1>
        <div class="page-header__subtitle">Шаблоны, условия и приоритеты</div>
      </div>
      <button class="btn btn--primary" type="button" @click="edit()">Новая карточка</button>
      <button class="btn btn--sm" type="button" @click="createDefaults">Создать стандартные</button>
      <button class="btn btn--sm" type="button" @click="exportJson">Выгрузить JSON</button>
      <label class="btn btn--sm">Загрузить JSON<input type="file" accept="application/json" hidden @change="importJson" /></label>
      <RouterLink class="btn btn--sm" :to="{ name: 'admin' }">Кабинет</RouterLink>
    </div>

    <div class="admin-layout">
      <AdminNav />
      <div class="admin-layout__main stack">
    <p v-if="error" class="notice notice--danger">{{ error }}</p>

    <div v-if="editing" class="card stack">
      <h2>{{ editing.id ? 'Изменить карточку' : 'Новая карточка' }}</h2>
      <div class="row" style="align-items: flex-end; flex-wrap: wrap">
        <div class="field"><label class="field__label">Название</label><input v-model="editing.name" class="input" /></div>
        <div class="field"><label class="field__label">Тип объекта</label>
          <select v-model="editing.objectType" class="select"><option v-for="cls in classes" :key="cls" :value="cls">{{ cls }}</option></select>
        </div>
        <div class="field"><label class="field__label">Приоритет</label><input v-model.number="editing.priority" class="input" type="number" min="0" /></div>
        <label class="row"><input v-model="editing.isDefault" type="checkbox" /> по умолчанию</label>
      </div>
      <div class="field">
        <label class="field__label">Условие</label>
        <input v-model="editing.formula" class="input" placeholder="type = 'ARTEFACT' and magic = true" />
        <span class="field__hint">Ошибка в формуле считается ложью. true — подходит всем. Пример: race = 'Эльф'</span>
      </div>
      <h3>Предпросмотр</h3>
      <p class="faint" style="margin: 0">
        Нажмите на блок, чтобы выделить. Тяните за точки на углах — размер, за тело — положение.
      </p>
      <div class="card-canvas" @pointerdown.self="selectedIndex = null">
        <div v-if="(editing.blocks?.length ?? 0) === 0" class="faint" style="padding: 12px">
          Добавьте поля кнопкой ниже — они появятся здесь.
        </div>
        <div
          v-for="(block, index) in editing.blocks"
          :key="index"
          class="card-canvas__block"
          :class="{ 'card-canvas__block--active': selectedIndex === index }"
          :style="{ left: block.x + 'px', top: block.y + 'px', width: block.width + 'px', height: block.height + 'px', fontSize: block.fontSize + 'px', color: block.color || undefined }"
          @pointerdown.prevent="selectAndDrag(index, $event, 'move')"
        >
          <template v-if="block.field === 'text' || block.text">{{ block.text }}</template>
          <template v-else-if="block.field === 'avatar'">
            <img class="card-canvas__avatar" src="/icons/icon-192.svg" alt="аватар" />
          </template>
          <template v-else-if="block.display === 'ICON'">
            <svg
              v-if="previewIconKind(block.field)"
              class="card-canvas__icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path :d="typeIconPath(previewIconKind(block.field)!, previewIconValue(block.field))" />
            </svg>
            <span
              v-else-if="block.field === 'klass'"
              class="card-canvas__mask"
              :style="{ maskImage: 'url(' + classIconUrl('Волшебник') + ')', WebkitMaskImage: 'url(' + classIconUrl('Волшебник') + ')' }"
            />
            <span v-else-if="block.field === 'gender'">♀</span>
            <span v-else-if="block.field === 'alive'">♥</span>
            <span v-else>◆</span>
          </template>
          <template v-else>{{ blockValue(block.field) }}</template>
          <span
            v-for="corner in corners"
            :key="corner"
            class="card-canvas__handle"
            :class="'card-canvas__handle--' + corner"
            @pointerdown.stop.prevent="selectAndDrag(index, $event, corner)"
          />
        </div>
      </div>

      <h3>Поля</h3>
      <div
        v-for="(block, index) in editing.blocks"
        :key="'fields-' + index"
        class="row"
        style="flex-wrap: wrap; align-items: flex-end"
        :class="{ 'field-row--active': selectedIndex === index }"
        @click="selectedIndex = index"
      >
        <template v-if="block.field === 'text'">
          <div class="field"><label class="field__label">Текст</label><input v-model="block.text" class="input" /></div>
        </template>
        <template v-else>
          <div class="field"><label class="field__label">Поле</label>
            <select v-model="block.field" class="select"><option v-for="field in fields" :key="field" :value="field">{{ field }}</option></select>
          </div>
          <div class="field"><label class="field__label">Вид</label>
            <select v-model="block.display" class="select"><option value="TEXT">текст</option><option value="ICON">значок</option></select>
          </div>
        </template>
        <div class="field" style="width: 70px"><label class="field__label">Шрифт</label><input v-model.number="block.fontSize" class="input" type="number" min="8" /></div>
        <div class="field" style="width: 70px"><label class="field__label">Цвет</label><input v-model="block.color" class="input" type="color" /></div>
        <div class="field" style="width: 70px"><label class="field__label">X</label><input v-model.number="block.x" class="input" type="number" /></div>
        <div class="field" style="width: 70px"><label class="field__label">Y</label><input v-model.number="block.y" class="input" type="number" /></div>
        <div class="field" style="width: 80px"><label class="field__label">Ш</label><input v-model.number="block.width" class="input" type="number" /></div>
        <div class="field" style="width: 80px"><label class="field__label">В</label><input v-model.number="block.height" class="input" type="number" /></div>
        <button class="btn btn--danger btn--sm" type="button" @click.stop="editing.blocks?.splice(index, 1)">Убрать</button>
      </div>
      <div class="row"><button class="btn btn--sm" type="button" @click="addBlock">Добавить поле</button><button class="btn btn--sm" type="button" @click="addText">Добавить текст</button></div>
      <div class="row"><button class="btn btn--primary" type="button" @click="save">Сохранить</button><button class="btn" type="button" @click="editing = null">Отмена</button></div>
    </div>

    <div v-for="template in templates" :key="template.id" class="card stack stack--sm">
      <div class="row row--between"><strong>{{ template.name }}</strong><span class="badge">{{ template.objectType }}</span><span class="faint">приоритет {{ template.priority }}</span><span v-if="template.isDefault" class="badge badge--info">по умолчанию</span></div>
      <code>{{ template.formula }}</code>
      <div class="faint">полей: {{ template.blocks?.length ?? 0 }}</div>
      <div class="row"><button class="btn btn--sm" type="button" @click="edit(template)">Изменить</button>
        <button class="btn btn--sm" type="button" @click="duplicate(template)">Копировать</button><button class="btn btn--danger btn--sm" type="button" @click="remove(template)">Удалить</button></div>
    </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card-canvas {
  position: relative;
  user-select: none;
  -webkit-user-select: none;
  width: 100%;
  max-width: 300px;
  height: 110px;
  border: 1px dashed #8a8aa3;
  border-radius: 10px;
  background-color: #fafafa;
  background-image:
    linear-gradient(to right, rgba(30,30,47,.08) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(30,30,47,.08) 1px, transparent 1px);
  background-size: 10px 10px;
  overflow: hidden;
  touch-action: none;
}
.card-canvas__block {
  position: absolute;
  box-sizing: border-box;
  user-select: none;
  -webkit-user-select: none;
  display: flex;
  align-items: center;
  padding: 2px 4px;
  border: 1px solid #4fc3f7;
  border-radius: 4px;
  background: rgba(79, 195, 247, .16);
  color: #10222e;
  cursor: move;
  overflow: hidden;
  white-space: nowrap;
}
.card-canvas__block--active {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
  background: rgba(79, 195, 247, .3);
}
.card-canvas__avatar { width: 100%; height: 100%; object-fit: cover; border-radius: 8px; }
.card-canvas__icon { width: 100%; height: 100%; max-width: 26px; max-height: 26px; }
.card-canvas__mask { width: 100%; height: 100%; max-width: 24px; max-height: 24px; background: currentColor; -webkit-mask: center / contain no-repeat; mask: center / contain no-repeat; }
.card-canvas__handle {
  position: absolute;
  width: 9px;
  height: 9px;
  border: 1px solid #fff;
  border-radius: 50%;
  background: var(--accent);
}
.card-canvas__handle--nw { left: -5px; top: -5px; cursor: nwse-resize; }
.card-canvas__handle--ne { right: -5px; top: -5px; cursor: nesw-resize; }
.card-canvas__handle--sw { left: -5px; bottom: -5px; cursor: nesw-resize; }
.card-canvas__handle--se { right: -5px; bottom: -5px; cursor: nwse-resize; }
.card-canvas__handle--n { left: calc(50% - 4.5px); top: -5px; cursor: ns-resize; }
.card-canvas__handle--s { left: calc(50% - 4.5px); bottom: -5px; cursor: ns-resize; }
.card-canvas__handle--e { right: -5px; top: calc(50% - 4.5px); cursor: ew-resize; }
.card-canvas__handle--w { left: -5px; top: calc(50% - 4.5px); cursor: ew-resize; }
.field-row--active { outline: 1px solid var(--accent); outline-offset: 4px; border-radius: 6px; }
.admin-layout { display: grid; grid-template-columns: 200px minmax(0, 1fr); gap: 12px; align-items: start; }
.admin-layout__main { min-width: 0; }
@media (max-width: 760px) { .admin-layout { grid-template-columns: 1fr; } }
</style>
