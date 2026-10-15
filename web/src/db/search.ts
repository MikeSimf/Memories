import { listEntities } from './entities'
import { listDrafts, listRetellings } from './retellings'
import type { EntityType } from '@/api/types'

export interface LocalSearchItem {
  kind: 'ENTITY' | 'RETELLING'
  id: string
  type: EntityType | null
  title: string
  /** Фрагмент с подсветкой `<b>…</b>` — той же формы, что отдаёт сервер (`api.md` §8). */
  snippet: string
  revision: number
  updatedAt: string
  /** Пересказ-черновик: существует только на устройстве. */
  isDraft: boolean
}

export interface LocalSearchOptions {
  types?: EntityType[]
  includeRetellings?: boolean
  limit?: number
}

export interface LocalSearchResult {
  total: number
  items: LocalSearchItem[]
}

function normalize(value: string): string {
  return value.toLowerCase().replace(/ё/g, 'е')
}

function tokenize(query: string): string[] {
  return normalize(query)
    .split(/\s+/)
    .map((token) => token.trim())
    .filter((token) => token.length > 0)
}

function matches(haystack: string, tokens: string[]): boolean {
  const normalized = normalize(haystack)
  return tokens.every((token) => normalized.includes(token))
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const SNIPPET_WINDOW = 70

/**
 * Фрагмент текста вокруг первого совпадения с подсветкой `<b>`.
 * Экранирование выполняется до вставки тегов, поэтому HTML из данных не проходит.
 */
export function highlightSnippet(text: string, query: string, window = SNIPPET_WINDOW): string {
  const tokens = tokenize(query)
  const flat = text.replace(/\s+/g, ' ').trim()
  if (tokens.length === 0) {
    return escapeHtml(flat.slice(0, window * 2))
  }
  const normalized = normalize(flat)
  let index = -1
  for (const token of tokens) {
    const found = normalized.indexOf(token)
    if (found !== -1 && (index === -1 || found < index)) {
      index = found
    }
  }
  if (index === -1) {
    return escapeHtml(flat.slice(0, window * 2))
  }
  const start = Math.max(0, index - window)
  const end = Math.min(flat.length, index + window)
  const head = start > 0 ? '…' : ''
  const tail = end < flat.length ? '…' : ''
  let fragment = escapeHtml(flat.slice(start, end))
  for (const token of tokens) {
    const escaped = escapeHtml(token).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    if (!escaped) continue
    fragment = fragment.replace(new RegExp(escaped, 'gi'), (match) => `<b>${match}</b>`)
  }
  return `${head}${fragment}${tail}`
}

/**
 * Поиск по локальной базе — та же логика, что у сервера (`api.md` §8):
 * регистронезависимый по `name`, `summary`, `description`, `tags` и `body` пересказов.
 * Поэтому поиск работает без сети.
 */
export async function searchLocal(
  campaignId: string,
  query: string,
  options: LocalSearchOptions = {},
): Promise<LocalSearchResult> {
  const tokens = tokenize(query)
  if (tokens.length === 0) {
    return { total: 0, items: [] }
  }
  const limit = options.limit ?? 50
  const includeRetellings = options.includeRetellings !== false
  const typeFilter = options.types && options.types.length > 0 ? new Set(options.types) : null

  const scored: Array<{ score: number; item: LocalSearchItem }> = []

  const entities = await listEntities(campaignId)
  for (const entity of entities) {
    if (typeFilter && !typeFilter.has(entity.type)) continue
    const title = entity.name
    const haystack = [entity.name, entity.summary, entity.description, entity.tags.join(' ')].join('\n')
    if (!matches(haystack, tokens)) continue
    const titleHit = matches(title, tokens) ? 0 : 1
    const snippetSource = matches(entity.summary, tokens)
      ? entity.summary
      : matches(entity.description, tokens)
        ? entity.description
        : entity.summary || entity.description || title
    scored.push({
      score: titleHit * 1000 + title.length,
      item: {
        kind: 'ENTITY',
        id: entity.id,
        type: entity.type,
        title,
        snippet: highlightSnippet(snippetSource, query),
        revision: entity.revision,
        updatedAt: entity.updatedAt,
        isDraft: false,
      },
    })
  }

  if (includeRetellings) {
    const retellings = await listRetellings(campaignId)
    for (const retelling of retellings) {
      const haystack = `${retelling.title}\n${retelling.body}`
      if (!matches(haystack, tokens)) continue
      const titleHit = matches(retelling.title, tokens) ? 0 : 1
      scored.push({
        score: titleHit * 1000 + retelling.title.length,
        item: {
          kind: 'RETELLING',
          id: retelling.id,
          type: null,
          title: retelling.title,
          snippet: highlightSnippet(retelling.body || retelling.title, query),
          revision: retelling.revision,
          updatedAt: retelling.updatedAt,
          isDraft: retelling.status === 'DRAFT',
        },
      })
    }

    const drafts = await listDrafts(campaignId)
    for (const draft of drafts) {
      const haystack = `${draft.title}\n${draft.body}`
      if (!matches(haystack, tokens)) continue
      const titleHit = matches(draft.title, tokens) ? 0 : 1
      scored.push({
        score: titleHit * 1000 + draft.title.length,
        item: {
          kind: 'RETELLING',
          id: draft.id,
          type: null,
          title: draft.title,
          snippet: highlightSnippet(draft.body || draft.title, query),
          revision: 0,
          updatedAt: draft.updatedAt,
          isDraft: true,
        },
      })
    }
  }

  scored.sort((a, b) => a.score - b.score || a.item.title.localeCompare(b.item.title, 'ru'))
  return {
    total: scored.length,
    items: scored.slice(0, limit).map((entry) => entry.item),
  }
}
