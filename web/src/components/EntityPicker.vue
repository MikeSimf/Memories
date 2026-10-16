<script setup lang="ts">
import { computed } from 'vue'

import { OBJECT_CLASS_LABELS, type ObjectClass } from '@/api/types'
import { useCampaignStore } from '@/stores/campaign'

const props = withDefaults(
  defineProps<{
    modelValue: string | null
    /** Ограничение по классам объектов (например, только герои). */
    classes?: ObjectClass[]
    placeholder?: string
    emptyLabel?: string
  }>(),
  { placeholder: '', emptyLabel: '— не выбрано —', classes: undefined },
)

const emit = defineEmits<{ (event: 'update:modelValue', value: string | null): void }>()

const campaign = useCampaignStore()

const options = computed(() =>
  campaign.objectOptions.filter(
    (option) => !props.classes || props.classes.length === 0 || props.classes.includes(option.objectClass),
  ),
)

/** Объект может быть удалён или ещё не загружен — показываем его отдельной строкой. */
const knownValue = computed(() => (props.modelValue ? String(props.modelValue) : ''))
const missingOption = computed(
  () => knownValue.value !== '' && !options.value.some((option) => option.id === knownValue.value),
)

function onChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  emit('update:modelValue', value === '' ? null : value)
}
</script>

<template>
  <select class="select" :value="knownValue" @change="onChange">
    <option value="">{{ emptyLabel }}</option>
    <option v-if="missingOption" :value="knownValue">
      {{ campaign.objectName(knownValue) || 'не найден на устройстве' }}
    </option>
    <option v-for="option in options" :key="option.id" :value="option.id">
      {{ option.name }} — {{ OBJECT_CLASS_LABELS[option.objectClass] }}
    </option>
  </select>
</template>
