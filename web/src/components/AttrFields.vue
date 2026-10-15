<script setup lang="ts">
import { computed } from 'vue'

import type { EntityAttrs, EntityType } from '@/api/types'
import EntityPicker from './EntityPicker.vue'
import { attrFieldsFor } from '@/utils/attrs'

const props = defineProps<{ type: EntityType; attrs: EntityAttrs }>()
const emit = defineEmits<{ (event: 'update:attrs', value: EntityAttrs): void }>()

const fields = computed(() => attrFieldsFor(props.type))
const knownKeys = computed(() => new Set(fields.value.map((field) => field.key)))
/** Дополнительные ключи, которые пришли от другого клиента: сохраняем как есть. */
const extraKeys = computed(() => Object.keys(props.attrs).filter((key) => !knownKeys.value.has(key)))

function setValue(key: string, value: unknown): void {
  emit('update:attrs', { ...props.attrs, [key]: value })
}

function inputValue(key: string): string {
  const value = props.attrs[key]
  if (value === undefined || value === null) return ''
  return String(value)
}

function onTextInput(key: string, event: Event): void {
  const value = (event.target as HTMLInputElement).value
  setValue(key, value === '' ? undefined : value)
}
</script>

<template>
  <div class="stack">
    <div class="attr-grid">
      <div v-for="field in fields" :key="field.key" class="field">
        <label class="field__label" :for="`attr-${field.key}`">{{ field.label }}</label>

        <select
          v-if="field.kind === 'select'"
          :id="`attr-${field.key}`"
          class="select"
          :value="inputValue(field.key)"
          @change="onTextInput(field.key, $event)"
        >
          <option value="">—</option>
          <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>

        <EntityPicker
          v-else-if="field.kind === 'entity'"
          :model-value="inputValue(field.key) || null"
          :types="field.entityTypes ? [...field.entityTypes] : undefined"
          @update:model-value="setValue(field.key, $event)"
        />

        <input
          v-else
          :id="`attr-${field.key}`"
          class="input"
          :type="field.kind === 'number' ? 'number' : 'text'"
          :min="field.min"
          :max="field.max"
          :placeholder="field.placeholder"
          :value="inputValue(field.key)"
          @input="onTextInput(field.key, $event)"
        />
      </div>

      <div v-for="key in extraKeys" :key="key" class="field">
        <label class="field__label" :for="`attr-extra-${key}`">{{ key }}</label>
        <input
          :id="`attr-extra-${key}`"
          class="input"
          type="text"
          :value="inputValue(key)"
          @input="onTextInput(key, $event)"
        />
      </div>
    </div>
    <p v-if="fields.length === 0" class="faint">Для этого типа объектов дополнительных полей нет.</p>
  </div>
</template>
