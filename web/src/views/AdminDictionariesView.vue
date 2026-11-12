<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminNav from '@/components/AdminNav.vue'

import { adminApi, errorMessage } from '@/api'
import { classIconUrl } from '@/ui/classIcons'
import { typeIconPath } from '@/ui/typeIcons'

const data = ref<Record<string, string[]>>({})
const route = useRoute()
const kind = ref(String(route.query.kind ?? 'RACE'))
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

async function rename(value: string): Promise<void> {
  const next = prompt('Новое название', value)
  if (!next || next.trim() === value) return
  try {
    await adminApi.addDictionary(kind.value, next.trim())
    await adminApi.removeDictionary(kind.value, value)
    await load()
  } catch (caught) {
    error.value = errorMessage(caught)
  }
}

function iconKind(): 'RACE' | 'LOCATION' | 'ITEM' | null {
  if (kind.value === 'RACE') return 'RACE'
  if (kind.value === 'ITEM_TYPE') return 'ITEM'
  if (kind.value === 'LOCATION_TYPE') return 'LOCATION'
  return null
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

watch(() => route.query.kind, (value) => {
  if (typeof value === 'string') kind.value = value
  void load()
}, { immediate: true })

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
    <div class="admin-layout">
      <AdminNav />
      <div class="admin-layout__main stack">
    <div class="row">
      <select v-model="kind" class="select">
        <option v-for="value in kinds" :key="value" :value="value">{{ titles[value] || value }}</option>
      </select>
      <input v-model="name" class="input" placeholder="Новое значение" />
      <button class="btn btn--primary" type="button" @click="add">Добавить</button>
    </div>
    <div class="card stack">
      <div class="dict-row dict-row--head"><span>Название</span><span>Картинка</span><span></span></div>
      <div v-for="item in items" :key="item" class="dict-row">
        <span>{{ item }}</span>
        <span class="dict-icon">
          <svg v-if="iconKind()" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <path :d="typeIconPath(iconKind()!, item)" />
          </svg>
          <span
            v-else-if="kind === 'CLASS' && classIconUrl(item)"
            class="dict-icon__mask"
            :style="{ maskImage: 'url(' + classIconUrl(item) + ')', WebkitMaskImage: 'url(' + classIconUrl(item) + ')' }"
          />
        </span>
        <span class="row">
          <button class="btn btn--sm" type="button" @click="rename(item)">Изменить</button>
          <button class="btn btn--danger btn--sm" type="button" @click="remove(item)">Удалить</button>
        </span>
      </div>
    </div>
      </div>
    </div>
  </div>
</template>
