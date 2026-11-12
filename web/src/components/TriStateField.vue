<script setup lang="ts">
/** Nullable Boolean: TRUE — отмечено, FALSE — пусто, NULL — вопрос. */
const props = withDefaults(
  defineProps<{
    label: string
    modelValue: boolean | null
    trueLabel?: string
    falseLabel?: string
    nullLabel?: string
  }>(),
  { trueLabel: 'да', falseLabel: 'нет', nullLabel: 'неизвестно' },
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean | null] }>()

function cycle(): void {
  emit('update:modelValue', props.modelValue === true ? false : props.modelValue === false ? null : true)
}
</script>

<template>
  <div class="field">
    <span class="field__label">{{ label }}</span>
    <button class="tri-state" type="button" @click="cycle">
      <span class="tri-state__box" :class="{
        'tri-state__box--true': modelValue === true,
        'tri-state__box--null': modelValue === null,
      }">
        {{ modelValue === true ? '✓' : modelValue === null ? '?' : '' }}
      </span>
      <span>{{ modelValue === true ? trueLabel : modelValue === false ? falseLabel : nullLabel }}</span>
    </button>
  </div>
</template>

<style scoped>
.tri-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  background: none;
  border: 0;
  color: inherit;
  cursor: pointer;
  font: inherit;
}
.tri-state__box {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 2px solid var(--border);
  border-radius: 4px;
  font-weight: 700;
}
.tri-state__box--true { border-color: #2e7d32; color: #2e7d32; }
.tri-state__box--null { border-color: #b36b00; color: #b36b00; }
</style>
