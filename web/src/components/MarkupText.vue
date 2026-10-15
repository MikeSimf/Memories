<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useCampaignStore } from '@/stores/campaign'
import { renderMarkup, sanitizeSnippet } from '@/utils/markdown'

const props = withDefaults(
  defineProps<{
    text: string | null | undefined
    /** Готовый HTML с подсветкой (фрагмент поиска с сервера или из локальной базы). */
    snippet?: boolean
  }>(),
  { snippet: false },
)

const emit = defineEmits<{ (event: 'broken-link', payload: { name: string }): void }>()

const router = useRouter()
const campaign = useCampaignStore()

const rendered = computed(() => {
  const source = props.text ?? ''
  if (props.snippet) {
    return { html: sanitizeSnippet(source), links: [] }
  }
  return renderMarkup(source, { resolver: campaign.linkResolver })
})

/** Переход по ссылке `[[…]]`: разрешённая открывает карточку, «битая» — создание. */
function onClick(event: MouseEvent): void {
  const element = event.target as HTMLElement | null
  const anchor = element?.closest('a.wikilink')
  if (!anchor) return
  event.preventDefault()
  const resolved = anchor.getAttribute('data-wiki-resolved') === '1'
  const id = anchor.getAttribute('data-wiki-id') ?? ''
  const name = anchor.getAttribute('data-wiki-name') ?? ''
  if (resolved && id && campaign.campaignId) {
    void router.push({
      name: 'entity-detail',
      params: { campaignId: campaign.campaignId, entityId: id },
    })
    return
  }
  emit('broken-link', { name: name || anchor.textContent || '' })
}
</script>

<template>
  <div class="markup" v-html="rendered.html" @click="onClick" />
</template>
