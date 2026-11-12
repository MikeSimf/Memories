<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { cardsApi, errorMessage, type CardTemplateDto } from '@/api'
import type { ObjectClass } from '@/api/types'

const templates = ref<CardTemplateDto[]>([])
const error = ref<string | null>(null)
const editing = ref<Partial<CardTemplateDto> | null>(null)
const fields = ['avatar', 'name', 'type', 'race', 'klass', 'gender', 'alive', 'shortDescription', 'itemType', 'locationType', 'magic', 'hasSetting', 'ownerName']
const classes: ObjectClass[] = ['CHARACTER', 'HERO', 'LOCATION', 'ITEM']
const selectedIndex = ref<number | null>(null)
const corners = ['nw', 'ne', 'sw', 'se'] as const
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

function iconValue(field: string): string {
  return { race: '🧝', klass: '✦', gender: '♀', alive: '♥', type: '◆', itemType: '⚔', ownerName: '♜', avatar: '▣' }[field] ?? '◆'
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
      <RouterLink class="btn btn--sm" :to="{ name: 'admin' }">Кабинет</RouterLink>
    </div>
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
        <span class="field__hint">Ошибка в формуле считается ложью. true — подходит всем.</span>
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
          :style="{ left: block.x + 'px', top: block.y + 'px', width: block.width + 'px', height: block.height + 'px', fontSize: block.fontSize + 'px' }"
          @pointerdown.prevent="selectAndDrag(index, $event, 'move')"
        >
          <template v-if="block.field === 'avatar'">аватар</template>
          <template v-else-if="block.display === 'ICON'">{{ iconValue(block.field) }}</template>
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
        <div class="field"><label class="field__label">Поле</label>
          <select v-model="block.field" class="select"><option v-for="field in fields" :key="field" :value="field">{{ field }}</option></select>
        </div>
        <div class="field"><label class="field__label">Вид</label>
          <select v-model="block.display" class="select"><option value="TEXT">текст</option><option value="ICON">значок</option></select>
        </div>
        <div class="field" style="width: 70px"><label class="field__label">X</label><input v-model.number="block.x" class="input" type="number" /></div>
        <div class="field" style="width: 70px"><label class="field__label">Y</label><input v-model.number="block.y" class="input" type="number" /></div>
        <div class="field" style="width: 80px"><label class="field__label">Ш</label><input v-model.number="block.width" class="input" type="number" /></div>
        <div class="field" style="width: 80px"><label class="field__label">В</label><input v-model.number="block.height" class="input" type="number" /></div>
        <button class="btn btn--danger btn--sm" type="button" @click.stop="editing.blocks?.splice(index, 1)">Убрать</button>
      </div>
      <button class="btn btn--sm" type="button" @click="addBlock">Добавить поле</button>
      <div class="row"><button class="btn btn--primary" type="button" @click="save">Сохранить</button><button class="btn" type="button" @click="editing = null">Отмена</button></div>
    </div>

    <div v-for="template in templates" :key="template.id" class="card stack stack--sm">
      <div class="row row--between"><strong>{{ template.name }}</strong><span class="badge">{{ template.objectType }}</span><span class="faint">приоритет {{ template.priority }}</span><span v-if="template.isDefault" class="badge badge--info">по умолчанию</span></div>
      <code>{{ template.formula }}</code>
      <div class="faint">полей: {{ template.blocks?.length ?? 0 }}</div>
      <div class="row"><button class="btn btn--sm" type="button" @click="edit(template)">Изменить</button><button class="btn btn--danger btn--sm" type="button" @click="remove(template)">Удалить</button></div>
    </div>
  </div>
</template>

<style scoped>
.card-canvas {
  position: relative;
  user-select: none;
  -webkit-user-select: none;
  width: 100%;
  max-width: 420px;
  height: 160px;
  border: 1px dashed var(--border);
  border-radius: 10px;
  background: var(--bg-elev);
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
  border: 1px solid color-mix(in srgb, var(--accent) 45%, transparent);
  border-radius: 4px;
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  cursor: move;
  overflow: hidden;
  white-space: nowrap;
}
.card-canvas__block--active {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
  background: color-mix(in srgb, var(--accent) 18%, transparent);
}
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
.field-row--active { outline: 1px solid var(--accent); outline-offset: 4px; border-radius: 6px; }
</style>
