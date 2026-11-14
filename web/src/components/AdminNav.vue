<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const dictionaryKinds = [
  { key: 'RACE', label: 'Расы' },
  { key: 'CLASS', label: 'Классы' },
  { key: 'ITEM_TYPE', label: 'Предметы' },
  { key: 'LOCATION_TYPE', label: 'Локации' },
]
</script>

<template>
  <aside class="admin-nav">
    <RouterLink class="admin-nav__item" :to="{ name: 'admin', query: { section: 'users' } }">Пользователи</RouterLink>
    <RouterLink class="admin-nav__item" :to="{ name: 'admin', query: { section: 'feedback' } }">Обратная связь</RouterLink>
    <RouterLink class="admin-nav__item" :to="{ name: 'admin', query: { section: 'crashes' } }">Ошибки</RouterLink>

    <div class="admin-nav__title">Справочники</div>
    <RouterLink
      v-for="kind in dictionaryKinds"
      :key="kind.key"
      class="admin-nav__item admin-nav__item--child"
      :class="{ 'admin-nav__item--active': route.name === 'admin-dictionaries' && route.query.kind === kind.key }"
      :to="{ name: 'admin-dictionaries', query: { kind: kind.key } }"
    >
      {{ kind.label }}
    </RouterLink>

    <RouterLink class="admin-nav__item" :to="{ name: 'admin-cards' }">Карточки</RouterLink>
  </aside>
</template>

<style scoped>
.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 220px;
  min-width: 220px;
  padding: 8px;
  border-right: 1px solid var(--border);
}
.admin-nav__item {
  padding: 8px 10px;
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
}
.admin-nav__item:hover { background: var(--bg-elev-2, rgba(127,127,127,.12)); }
.admin-nav__item--active { background: var(--accent); color: #fff; }
.admin-nav__item--child { padding-left: 22px; font-size: .92rem; }
.admin-nav__title { margin: 9px 10px 3px; font-size: .75rem; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; color: var(--text-muted); }
</style>
