<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

/** Экраны входа, OAuth и приглашения рисуются без общей шапки. */
const bare = computed(() => route.meta.bare === true)
const narrow = computed(() => route.meta.narrow === true)
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="!bare && auth.isAuthenticated" />
    <main class="app-main" :class="{ 'app-main--narrow': narrow }">
      <RouterView />
    </main>
    <ToastHost />
  </div>
</template>
