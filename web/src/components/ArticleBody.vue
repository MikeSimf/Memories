<script setup lang="ts">
import { computed } from 'vue'

/** Безопасный вывод HTML статьи: только абзацы, форматирование и картинки. */
const props = defineProps<{ html: string }>()

const ALLOWED_TAGS = new Set(['P','BR','STRONG','B','EM','I','U','S','H1','H2','H3','BLOCKQUOTE','UL','OL','LI','FIGURE','FIGCAPTION','IMG','A','SPAN','DIV'])
const ALLOWED_STYLE = ['font-family','font-size','text-indent','text-align']

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
        } else if (!((child.tagName === 'IMG' && name === 'src') || (child.tagName === 'A' && name === 'href') || name === 'alt')) {
          child.removeAttribute(attr.name)
        }
      }
      if (child.tagName === 'IMG') {
        const src = child.getAttribute('src') ?? ''
        if (!src.startsWith('/api/files/') && !src.startsWith('https://')) child.remove()
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

const safeHtml = computed(() => sanitize(props.html))
</script>

<template>
  <div class="article-body" v-html="safeHtml" />
</template>

<style scoped>
.article-body { line-height: 1.7; }
.article-body :deep(p) { margin: 0 0 .9rem; }
.article-body :deep(figure) { margin: 1.1rem 0; }
.article-body :deep(img) { max-width: 100%; height: auto; border-radius: 10px; }
.article-body :deep(figcaption) { color: var(--text-muted); font-size: .85rem; padding-top: .35rem; }
.article-body :deep(blockquote) { margin: 1rem 0; padding: .5rem 1rem; border-left: 3px solid var(--accent); color: var(--text-muted); }
.article-body :deep(a) { color: var(--accent); }
</style>
