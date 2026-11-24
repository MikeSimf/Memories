<script setup lang="ts">
/** Панель сортировки: поле и направление четырьмя квадратными кнопками. */
const props = defineProps<{
  modelValue: string
  ascending: boolean
  options: Array<{ value: string; label: string }>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:ascending': [value: boolean]
}>()

function choose(value: string): void {
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="sort-panel" role="group" aria-label="Сортировка">
    <button
      v-for="option in options"
      :key="option.value"
      class="sort-panel__button"
      :class="{ 'sort-panel__button--active': modelValue === option.value }"
      type="button"
      :title="option.label"
      :aria-label="option.label"
      :aria-pressed="modelValue === option.value"
      @click="choose(option.value)"
    >
      <span v-if="option.value === 'name'" class="sort-panel__aa">Аа</span>
      <svg v-else-if="option.value === 'createdAt'" class="sort-panel__icon" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="8.2" fill="none" stroke="currentColor" stroke-width="1.7" />
        <path d="M12 7.5V12l2.8 1.8" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
        <text x="16.2" y="6.2" font-size="6.5" fill="currentColor" font-weight="700">1</text>
      </svg>
      <svg v-else class="sort-panel__icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4.5 9.5a8 8 0 1 1 1.2 8" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
        <path d="M4.5 4.8v4.7h4.7" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
        <path d="M12 8.5V12l2.5 1.5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
      </svg>
    </button>
    <button
      class="sort-panel__button"
      type="button"
      :title="ascending ? 'по возрастанию' : 'по убыванию'"
      :aria-label="ascending ? 'по возрастанию' : 'по убыванию'"
      @click="emit('update:ascending', !props.ascending)"
    >
      <span class="sort-panel__arrow">{{ ascending ? '↑' : '↓' }}</span>
    </button>
  </div>
</template>

<style scoped>
.sort-panel {
  display: flex;
  gap: .35rem;
  justify-content: flex-end;
}
.sort-panel__button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
}
.sort-panel__button:hover { border-color: var(--accent); color: var(--text); }
.sort-panel__button--active { border-color: var(--accent); background: var(--accent-soft, rgba(79,111,90,.14)); color: var(--text); }
.sort-panel__aa { font-size: .82rem; font-weight: 700; letter-spacing: -.04em; }
.sort-panel__icon { width: 22px; height: 22px; }
.sort-panel__arrow { font-size: 1.25rem; line-height: 1; }
</style>
