<script setup lang="ts">
withDefaults(defineProps<{ title: string; hint?: string }>(), { hint: '' })
</script>

<template>
  <div class="empty">
    <p><strong>{{ title }}</strong></p>
    <p v-if="hint" class="muted">{{ hint }}</p>
    <slot />
  </div>
</template>
