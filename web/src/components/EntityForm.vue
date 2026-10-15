<script setup lang="ts">
import { computed, ref } from 'vue'

import { ENTITY_TYPE_LABELS, ENTITY_TYPES, type EntityType } from '@/api/types'
import AttrFields from './AttrFields.vue'
import MarkupText from './MarkupText.vue'
import TagInput from './TagInput.vue'
import type { EntityFormValues } from '@/types/forms'

const props = withDefaults(
  defineProps<{
    modelValue: EntityFormValues
    mode?: 'create' | 'edit'
    saving?: boolean
    submitLabel?: string
    /** Разрешить смену типа (при создании). */
    allowTypeChange?: boolean
  }>(),
  { mode: 'create', saving: false, submitLabel: 'Сохранить', allowTypeChange: true },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: EntityFormValues): void
  (event: 'submit'): void
  (event: 'cancel'): void
}>()

const showPreview = ref(false)

const typeLocked = computed(() => props.mode === 'edit' || !props.allowTypeChange)

function patch(changes: Partial<EntityFormValues>): void {
  emit('update:modelValue', { ...props.modelValue, ...changes })
}

function submit(): void {
  emit('submit')
}
</script>

<template>
  <form class="stack" @submit.prevent="submit">
    <div class="row" style="align-items: flex-end">
      <div class="field" style="flex: 0 0 190px">
        <label class="field__label" for="entity-type">Тип объекта</label>
        <select
          id="entity-type"
          class="select"
          :value="modelValue.type"
          :disabled="typeLocked"
          @change="patch({ type: ($event.target as HTMLSelectElement).value as EntityType })"
        >
          <option v-for="type in ENTITY_TYPES" :key="type" :value="type">
            {{ ENTITY_TYPE_LABELS[type] }}
          </option>
        </select>
      </div>

      <div class="field" style="flex: 1; min-width: 220px">
        <label class="field__label" for="entity-name">Название</label>
        <input
          id="entity-name"
          class="input"
          type="text"
          required
          :value="modelValue.name"
          placeholder="Силдар Холлвинтер"
          @input="patch({ name: ($event.target as HTMLInputElement).value })"
        />
      </div>
    </div>

    <div class="field">
      <label class="field__label" for="entity-summary">Краткое описание</label>
      <input
        id="entity-summary"
        class="input"
        type="text"
        :value="modelValue.summary"
        placeholder="Мэр Фандалина"
        @input="patch({ summary: ($event.target as HTMLInputElement).value })"
      />
      <span class="field__hint">Показывается в списках и в результатах поиска.</span>
    </div>

    <div class="field">
      <label class="field__label" for="entity-tags">Теги</label>
      <TagInput :model-value="modelValue.tags" @update:model-value="patch({ tags: $event })" />
    </div>

    <div class="field">
      <label class="field__label">Поля по типу</label>
      <AttrFields
        :type="modelValue.type"
        :attrs="modelValue.attrs"
        @update:attrs="patch({ attrs: $event })"
      />
    </div>

    <div class="field">
      <div class="row row--between">
        <label class="field__label" for="entity-description">Описание</label>
        <button class="btn btn--ghost btn--sm" type="button" @click="showPreview = !showPreview">
          {{ showPreview ? 'Скрыть предпросмотр' : 'Предпросмотр разметки' }}
        </button>
      </div>
      <textarea
        id="entity-description"
        class="textarea textarea--tall"
        :value="modelValue.description"
        placeholder="Связан с [[Лира Ксенофонт|Лиры]] долгом…"
        @input="patch({ description: ($event.target as HTMLTextAreaElement).value })"
      />
      <span class="field__hint">
        Поддерживается markdown и ссылки на объекты: <code>[[Имя]]</code>,
        <code>[[Имя|подпись]]</code>, <code>[[#uuid|подпись]]</code>.
      </span>
      <div v-if="showPreview" class="preview-box">
        <MarkupText v-if="modelValue.description.trim()" :text="modelValue.description" />
        <span v-else class="faint">Пусто</span>
      </div>
    </div>

    <div class="row row--end">
      <button class="btn" type="button" :disabled="saving" @click="emit('cancel')">Отмена</button>
      <button class="btn btn--primary" type="submit" :disabled="saving || modelValue.name.trim() === ''">
        <span v-if="saving" class="spinner" aria-hidden="true" />
        {{ submitLabel }}
      </button>
    </div>
  </form>
</template>
