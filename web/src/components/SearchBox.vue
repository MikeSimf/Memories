<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    /** Задержка перед выдачей значения, мс. */
    debounceMs?: number
  }>(),
  { placeholder: 'Поиск…', debounceMs: 250 },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
}>()

const local = ref(props.modelValue)
let timer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.modelValue,
  (value) => {
    if (value !== local.value) local.value = value
  },
)

function onInput(): void {
  if (timer !== null) clearTimeout(timer)
  timer = setTimeout(() => emit('update:modelValue', local.value), props.debounceMs)
}

function clear(): void {
  local.value = ''
  emit('update:modelValue', '')
}

onBeforeUnmount(() => {
  if (timer !== null) clearTimeout(timer)
})
</script>

<template>
  <div class="row" style="gap: 0.35rem; flex: 1; min-width: 200px">
    <input
      v-model="local"
      class="input"
      type="search"
      :placeholder="placeholder"
      aria-label="Поиск"
      @input="onInput"
      @keydown.enter="emit('update:modelValue', local)"
    />
    <button v-if="local" class="btn btn--ghost btn--sm" type="button" aria-label="Очистить" @click="clear">
      ✕
    </button>
  </div>
</template>
