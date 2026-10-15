<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ modelValue: string[]; placeholder?: string }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string[]): void }>()

const input = ref('')

function add(): void {
  const value = input.value.trim().replace(/,$/, '')
  if (value === '') return
  if (!props.modelValue.includes(value)) {
    emit('update:modelValue', [...props.modelValue, value])
  }
  input.value = ''
}

function remove(tag: string): void {
  emit(
    'update:modelValue',
    props.modelValue.filter((item) => item !== tag),
  )
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' || event.key === ',') {
    event.preventDefault()
    add()
  }
}
</script>

<template>
  <div class="stack stack--sm">
    <div class="row" style="flex-wrap: nowrap">
      <input
        v-model="input"
        class="input"
        type="text"
        :placeholder="placeholder ?? 'Тег и Enter'"
        @keydown="onKeydown"
        @blur="add"
      />
      <button class="btn btn--sm" type="button" @click="add">Добавить</button>
    </div>
    <div v-if="modelValue.length > 0" class="chips">
      <span v-for="tag in modelValue" :key="tag" class="chip">
        {{ tag }}
        <button class="chip__remove" type="button" :aria-label="`Убрать тег ${tag}`" @click="remove(tag)">
          ×
        </button>
      </span>
    </div>
  </div>
</template>
