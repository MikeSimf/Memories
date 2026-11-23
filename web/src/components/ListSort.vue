<script setup lang="ts">
/** Единый выбор сортировки списка: поле и направление. */
defineProps<{
  modelValue: string
  ascending: boolean
  options: Array<{ value: string; label: string }>
}>()
defineEmits<{
  'update:modelValue': [value: string]
  'update:ascending': [value: boolean]
}>()
</script>

<template>
  <div class="row list-sort">
    <select
      class="select"
      :value="modelValue"
      aria-label="Сортировка"
      @change="$emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <button
      class="btn btn--sm"
      type="button"
      :title="ascending ? 'по возрастанию' : 'по убыванию'"
      @click="$emit('update:ascending', !ascending)"
    >
      {{ ascending ? '↑' : '↓' }}
    </button>
  </div>
</template>
