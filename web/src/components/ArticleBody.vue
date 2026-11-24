<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchFileBlob } from '@/api'
import { useCampaignStore } from '@/stores/campaign'
import { parseWikiLinks, type WikiLinkMatch } from '@/utils/wikilinks'

/** Безопасный вывод HTML статьи: формат, ссылки [[…]] и картинки с авторизацией. */
const props = defineProps<{ html: string }>()
const router = useRouter()
const campaign = useCampaignStore()

const ALLOWED_TAGS = new Set(['P','BR','STRONG','B','EM','I','U','S','H1','H2','H3','BLOCKQUOTE','UL','OL','LI','FIGURE','FIGCAPTION','IMG','A','SPAN','DIV'])
const ALLOWED_STYLE = ['font-family','font-size','text-indent','text-align']
const objectUrls: string[] = []
let root: HTMLElement | null = null

function sanitize(input: string): string {
  const doc = new DOMParser().parseFromString(input, 'text/html')
  const walk = (node: Element): void => {
    for (const child of [...node.children]) {
      if (!ALLOWED_TAGS.has(child.tagName)) {
        child.replaceWith(...child.childNodes)
        continue
      }
      for (const attr of [...child.attributes]) {
        const name = attr.name.toLowerCase()
        if (name === 'style') {
          const safe = ALLOWED_STYLE
            .map((property) => `${property}: ${child.style.getPropertyValue(property)}`)
            .filter((value) => !value.endsWith(': '))
            .join('; ')
          if (safe) child.setAttribute('style', safe)
          else child.removeAttribute('style')
        } else if (!((child.tagName === 'IMG' && (name === 'src' || name === 'data-file-id')) || (child.tagName === 'A' && name === 'href') || name === 'alt')) {
          child.removeAttribute(attr.name)
        }
      }
      if (child.tagName === 'IMG') {
        const src = child.getAttribute('src') ?? ''
        const fileId = child.getAttribute('data-file-id')
        if (!fileId && !src.startsWith('/api/files/') && !src.startsWith('https://')) child.remove()
      }
      if (child.tagName === 'A') {
        child.setAttribute('rel', 'noopener noreferrer')
        child.setAttribute('target', '_blank')
      }
      walk(child)
    }
  }
  walk(doc.body)
  return doc.body.innerHTML
}

function wikiAnchor(match: WikiLinkMatch): HTMLAnchorElement {
  const resolved = campaign.linkResolver.resolve(match)
  const anchor = document.createElement('a')
  anchor.className = 'wikilink'
  anchor.textContent = match.label
  anchor.href = '#'
  anchor.dataset.wikiName = match.name ?? match.label
  if (resolved.resolved && resolved.resolvedId) {
    anchor.dataset.wikiResolved = '1'
    anchor.dataset.wikiId = resolved.resolvedId
  }
  return anchor
}

function replaceWikiLinks(rootEl: HTMLElement): void {
  const walker = document.createTreeWalker(rootEl, NodeFilter.SHOW_TEXT)
  const nodes: Text[] = []
  while (walker.nextNode()) nodes.push(walker.currentNode as Text)
  for (const node of nodes) {
    const text = node.nodeValue ?? ''
    if (!text.includes('[[') || node.parentElement?.closest('a')) continue
    const matches = parseWikiLinks(text)
    if (matches.length === 0) continue
    const fragment = document.createDocumentFragment()
    let cursor = 0
    for (const match of matches) {
      fragment.append(text.slice(cursor, match.start))
      fragment.append(wikiAnchor(match))
      cursor = match.end
    }
    fragment.append(text.slice(cursor))
    node.replaceWith(fragment)
  }
}

async function loadImages(): Promise<void> {
  if (!root) return
  for (const image of [...root.querySelectorAll('img')]) {
    const fileId = image.getAttribute('data-file-id')
      ?? image.getAttribute('src')?.match(/\/api\/files\/([0-9a-f-]+)/i)?.[1]
    if (!fileId || image.dataset.loaded === '1') continue
    try {
      const blob = await fetchFileBlob(fileId)
      const url = URL.createObjectURL(blob)
      objectUrls.push(url)
      image.src = url
      image.dataset.loaded = '1'
    } catch {
      image.remove()
    }
  }
}

function render(): void {
  // v-html создаёт DOM; после nextTick добавляем ссылки и картинки.
  void nextTick(() => {
    root = document.querySelector('.article-body') as HTMLElement | null
    if (!root) return
    replaceWikiLinks(root)
    void loadImages()
  })
}

const safeHtml = computed(() => sanitize(props.html))
watch(safeHtml, render, { immediate: true })

function onClick(event: MouseEvent): void {
  const anchor = (event.target as HTMLElement | null)?.closest('a.wikilink') as HTMLAnchorElement | null
  if (!anchor) return
  event.preventDefault()
  const id = anchor.dataset.wikiId
  if (anchor.dataset.wikiResolved === '1' && id && campaign.campaignId) {
    void router.push({ name: 'entity-detail', params: { campaignId: campaign.campaignId, entityId: id } })
  }
}

onBeforeUnmount(() => {
  for (const url of objectUrls) URL.revokeObjectURL(url)
})
</script>

<template>
  <div class="article-body" v-html="safeHtml" @click="onClick" />
</template>

<style scoped>
.article-body { line-height: 1.7; }
.article-body :deep(p) { margin: 0 0 .9rem; }
.article-body :deep(figure) { margin: 1.1rem 0; }
.article-body :deep(img) { max-width: 100%; height: auto; border-radius: 10px; }
.article-body :deep(img:not([src])) { display: none; }
.article-body :deep(figcaption) { color: var(--text-muted); font-size: .85rem; padding-top: .35rem; }
.article-body :deep(blockquote) { margin: 1rem 0; padding: .5rem 1rem; border-left: 3px solid var(--accent); color: var(--text-muted); }
.article-body :deep(a.wikilink) { color: var(--accent); text-decoration: underline dotted; }
</style>
