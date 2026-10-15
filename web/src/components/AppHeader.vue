<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import SyncIndicator from './SyncIndicator.vue'
import ThemeToggle from './ThemeToggle.vue'
import { useCampaignStore } from '@/stores/campaign'

const route = useRoute()
const campaign = useCampaignStore()

const campaignId = computed(() =>
  typeof route.params.campaignId === 'string' ? route.params.campaignId : null,
)
const title = computed(() => campaign.campaign?.title ?? '')
</script>

<template>
  <header class="app-header">
    <RouterLink class="app-header__brand" :to="{ name: 'campaigns' }">
      <img class="app-header__logo" src="/icons/icon-192.svg" alt="" />
      <span>DnD Memory</span>
    </RouterLink>

    <span v-if="campaignId && title" class="muted truncate" :title="title">· {{ title }}</span>

    <span class="spacer" />

    <SyncIndicator v-if="campaignId" :campaign-id="campaignId" />

    <ThemeToggle />

    <RouterLink
      class="btn btn--ghost btn--icon"
      :to="{ name: 'settings' }"
      aria-label="Настройки и профиль"
      title="Настройки и профиль"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <circle cx="12" cy="12" r="3.2" />
        <path
          d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-2.87 1.2V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-2.93-1.16l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15H4.5a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.16-2.93l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 11.5 4.6V4.5a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 2.93 1.16l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 19.4 11h.1a2 2 0 1 1 0 4h-.1Z"
        />
      </svg>
    </RouterLink>
  </header>
</template>
