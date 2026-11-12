<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { cardsApi, errorMessage, type CardTemplateDto } from '@/api'
import type { ObjectClass } from '@/api/types'

const templates = ref<CardTemplateDto[]>([])
const error = ref<string | null>(null)
const editing = ref<Partial<CardTemplateDto> | null>(null)
const fields = ['avatar', 'name', 'type', 'race', 'klass', 'gender', 'alive', 'shortDescription', 'itemType', 'locationType', 'magic', 'hasSetting', 'ownerName']
const classes: ObjectClass[] = ['CHARACTER', 'HERO', 'LOCATION', 'ITEM']
let drag: { index: number; mode: 'move' | 'resize'; startX: number; startY: number; x: number; y: number; w: number; h: number } | null = null

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

function startDrag(index: number, event: PointerEvent, mode: 'move' | 'resize'): void {
  const block = editing.value?.blocks?.[index]
  if (!block) return
  drag = { index, mode, startX: event.clientX, startY: event.clientY, x: block.x, y: block.y, w: block.width, h: block.height }
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
  } else {
    block.width = Math.max(20, Math.round(drag.w + dx))
    block.height = Math.max(12, Math.round(drag.h + dy))
  }
}

function stopDrag(): void {
  drag = null
  window.removeEventListener('pointermove', onDrag)
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
      <p class="faint" style="margin: 0">Перетаскивайте блоки, за правый нижний угол — размер. Появляется здесь, когда открыт редактор карточки.</p>
      <div class="card-canvas">
        <div v-if="(editing.blocks?.length ?? 0) === 0" class="faint" style="padding: 12px">
          Добавьте поля кнопкой ниже — они появятся здесь.
        </div>
        <div
          v-for="(block, index) in editing.blocks"
          :key="index"
          class="card-canvas__block"
          :style="{ left: block.x + 'px', top: block.y + 'px', width: block.width + 'px', height: block.height + 'px', fontSize: block.fontSize + 'px' }"
          @pointerdown="startDrag(index, $event, 'move')"
        >
          {{ block.field === 'avatar' ? 'аватар' : blockValue(block.field) }}
          <span class="card-canvas__resize" @pointerdown.stop="startDrag(index, $event, 'resize')" />
        </div>
      </div>

      <h3>Поля</h3>
      <div v-for="(block, index) in editing.blocks" :key="index" class="row" style="flex-wrap: wrap; align-items: flex-end">
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
        <button class="btn btn--danger btn--sm" type="button" @click="editing.blocks?.splice(index, 1)">Убрать</button>
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
<style scoped>
.card-canvas {
  position: relative;
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
  display: flex;
  align-items: center;
  padding: 2px 4px;
  border: 1px solid color-mix(in srgb, var(--accent) 55%, transparent);
  border-radius: 4px;
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  cursor: move;
  user-select: none;
  overflow: hidden;
  white-space: nowrap;
}
.card-canvas__resize {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: var(--accent);
  cursor: nwse-resize;
}
</style>
</template>
