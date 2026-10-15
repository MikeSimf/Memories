<script setup lang="ts">
import { computed } from 'vue'

import { ENTITY_TYPE_LABELS, type EntityType } from '@/api/types'
import { useCampaignStore } from '@/stores/campaign'

const props = withDefaults(
  defineProps<{
    modelValue: string | null
    /** Ограничение по типам объектов (например, только локации). */
    types?: EntityType[]
    placeholder?: string
    emptyLabel?: string
  }>(),
  { placeholder: '', emptyLabel: '— не выбрано —', types: undefined },
)

const emit = defineEmits<{ (event: 'update:modelValue', value: string | null): void }>()

const campaign = useCampaignStore()

const options = computed(() =>
  campaign.entityOptions.filter(
    (option) => !props.types || props.types.length === 0 || props.types.includes(option.type),
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
      {{ campaign.entityName(knownValue) || 'не найден на устройстве' }}
    </option>
    <option v-for="option in options" :key="option.id" :value="option.id">
      {{ option.name }} — {{ ENTITY_TYPE_LABELS[option.type] }}
    </option>
  </select>
</template>
