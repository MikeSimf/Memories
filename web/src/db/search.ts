import { listObjects } from './objects'
import { listDrafts, listRetellings } from './retellings'
import type { LocalObject } from './schema'
import type { ObjectClass, SearchKind } from '@/api/types'

export interface LocalSearchItem {
  kind: SearchKind
  id: string
  title: string
  /** Фрагмент с подсветкой `<b>…</b>` — той же формы, что отдаёт сервер (`api.md` §8). */
  snippet: string
  revision: number
  updatedAt: string
  /** Повествование-черновик: существует только на устройстве. */
  isDraft: boolean
}

export interface LocalSearchOptions {
  /** Виды объектов; пусто — все четыре класса. */
  classes?: ObjectClass[]
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
 * Проверка совпадения по набору полей: используется строкой поиска внутри вкладки.
 * Логика та же, что у поиска по кампании.
 */
export function matchesText(
  values: Array<string | null | undefined>,
  query: string,
): boolean {
  const tokens = tokenize(query)
  if (tokens.length === 0) return true
  return matches(values.filter((value): value is string => Boolean(value)).join('\n'), tokens)
}

/**
 * Поля объекта, по которым идёт поиск (`api.md` §8): имя и описание, у персонажа
 * дополнительно раса и краткое описание.
 */
export function objectSearchFields(object: LocalObject): string[] {
  const fields = [object.name, object.description]
  if (object.objectType === 'CHARACTER') {
    fields.push(object.race ?? '', object.shortDescription ?? '')
  }
  return fields.filter((value) => value !== '')
}

/** Живой фильтр вкладки: та же логика, что у локального поиска (`api.md` §8). */
export function objectMatchesQuery(object: LocalObject, query: string): boolean {
  return matchesText(objectSearchFields(object), query)
}

/**
 * Поиск по локальной базе — та же логика, что у сервера (`api.md` §8):
 * регистронезависимый по `name`, `description` (у персонажа ещё `race` и
 * `shortDescription`), у повествований — по заголовку и тексту. Работает без сети.
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
  const classFilter =
    options.classes && options.classes.length > 0 ? new Set<ObjectClass>(options.classes) : null

  const scored: Array<{ score: number; item: LocalSearchItem }> = []

  const objects = await listObjects(campaignId)
  for (const object of objects) {
    if (classFilter && !classFilter.has(object.objectType)) continue
    const title = object.name
    const haystack = objectSearchFields(object).join('\n')
    if (!matches(haystack, tokens)) continue
    const titleHit = matches(title, tokens) ? 0 : 1
    const snippetSource = matches(object.description, tokens)
      ? object.description
      : objectSearchFields(object).find((field) => matches(field, tokens)) ?? title
    scored.push({
      score: titleHit * 1000 + title.length,
      item: {
        kind: object.objectType,
        id: object.id,
        title,
        snippet: highlightSnippet(snippetSource, query),
        revision: object.revision,
        updatedAt: object.updatedAt,
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
