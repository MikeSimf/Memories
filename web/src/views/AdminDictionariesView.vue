<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { adminApi, errorMessage } from '@/api'

const data = ref<Record<string, string[]>>({})
const kind = ref('RACE')
const name = ref('')
const error = ref<string | null>(null)
const titles: Record<string, string> = {
  RACE: 'Расы', CLASS: 'Классы', ITEM_TYPE: 'Типы предметов', LOCATION_TYPE: 'Типы локаций',
}
const kinds = computed(() => Object.keys(data.value))
const items = computed(() => data.value[kind.value] ?? [])

async function load(): Promise<void> {
  try {
    data.value = await adminApi.dictionaries()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function add(): Promise<void> {
  if (!name.value.trim()) return
  try {
    await adminApi.addDictionary(kind.value, name.value.trim())
    name.value = ''
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

async function remove(value: string): Promise<void> {
  if (!confirm(`Удалить «${value}»?`)) return
  try {
    await adminApi.removeDictionary(kind.value, value)
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div class="page-header__titles">
        <h1>Справочники</h1>
        <div class="page-header__subtitle">Глобальные значения для всех кампаний</div>
      </div>
      <RouterLink class="btn btn--sm" :to="{ name: 'admin' }">Кабинет</RouterLink>
    </div>
    <p v-if="error" class="notice notice--danger">{{ error }}</p>
    <div class="row">
      <select v-model="kind" class="select">
        <option v-for="value in kinds" :key="value" :value="value">{{ titles[value] || value }}</option>
      </select>
      <input v-model="name" class="input" placeholder="Новое значение" />
      <button class="btn btn--primary" type="button" @click="add">Добавить</button>
    </div>
    <div class="card stack">
      <div v-for="item in items" :key="item" class="row row--between">
        <span>{{ item }}</span>
        <button class="btn btn--danger btn--sm" type="button" @click="remove(item)">Удалить</button>
      </div>
    </div>
  </div>
</template>
