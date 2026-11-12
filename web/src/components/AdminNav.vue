<script setup lang="ts">

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
    <details class="admin-nav__group" open>
      <summary>Справочники</summary>
      <RouterLink
        v-for="kind in dictionaryKinds"
        :key="kind.key"
        class="admin-nav__item admin-nav__item--child"
        :to="{ name: 'admin-dictionaries', query: { kind: kind.key } }"
      >
        {{ kind.label }}
      </RouterLink>
    </details>
    <RouterLink class="admin-nav__item" :to="{ name: 'admin-cards' }">Карточки</RouterLink>
  </aside>
</template>

<style scoped>
.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 190px;
  padding: 8px;
  border-right: 1px solid var(--border);
}
.admin-nav__item {
  padding: 8px 10px;
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
}
.admin-nav__item:hover, .admin-nav__item.router-link-active { background: var(--bg-elev-2, rgba(127,127,127,.12)); }
.admin-nav__item--child { padding-left: 26px; font-size: .92rem; }
.admin-nav__group summary { cursor: pointer; padding: 8px 10px; font-weight: 700; }
.admin-nav__group { display: flex; flex-direction: column; gap: 2px; }
</style>
