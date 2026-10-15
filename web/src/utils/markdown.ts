import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

import type { FenceState, LinkResolver, WikiLinkMatch } from './wikilinks'
import { emptyLinkResolver, trackFence } from './wikilinks'

/**
 * Рендер описаний: markdown + ссылки `[[…]]` (`docs/architecture.md` §4).
 *
 * Ссылки вырезаются до markdown-разбора (внутри блоков кода они не трогаются),
 * а после рендера подставляются якорями. Итоговый HTML проходит через DOMPurify,
 * поэтому разметка из данных не может внедрить скрипт.
 */

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
})

const PLACEHOLDER_PREFIX = 'dndwiki'

function placeholderAt(index: number): string {
  return `${PLACEHOLDER_PREFIX}${index}end`
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

interface ExtractedLink {
  placeholder: string
  match: WikiLinkMatch
}

/**
 * Заменяет `[[…]]` на текстовые маркеры, не задевая блоки и вставки кода.
 */
function extractWikiLinks(text: string): { text: string; links: ExtractedLink[] } {
  const links: ExtractedLink[] = []
  let result = ''
  let offset = 0
  const fence: FenceState = { marker: null }

  for (const line of text.split('\n')) {
    if (trackFence(fence, line)) {
      result += line + '\n'
      offset += line.length + 1
      continue
    }

    const regex = /(`+)([^`]*)`+/g
    let cursor = 0
    let match = regex.exec(line)
    while (match !== null) {
      result += replaceOutsideCode(line.slice(cursor, match.index), links, offset + cursor)
      result += match[0]
      cursor = match.index + match[0].length
      match = regex.exec(line)
    }
    result += replaceOutsideCode(line.slice(cursor), links, offset + cursor)
    result += '\n'
    offset += line.length + 1
  }

  // `join('\n')` даёт лишний перевод строки в конце — убираем его.
  if (result.endsWith('\n') && !text.endsWith('\n')) {
    result = result.slice(0, -1)
  }
  return { text: result, links }
}

function replaceOutsideCode(
  segment: string,
  links: ExtractedLink[],
  baseOffset: number,
): string {
  return segment.replace(/\[\[([^\]\n]+)\]\]/g, (raw, inner: string, position: number) => {
    const pipe = inner.indexOf('|')
    const target = (pipe === -1 ? inner : inner.slice(0, pipe)).trim()
    const labelSource = pipe === -1 ? '' : inner.slice(pipe + 1).trim()
    const isId = target.startsWith('#')
    const placeholder = placeholderAt(links.length)
    links.push({
      placeholder,
      match: {
        raw,
        name: isId ? null : target,
        targetId: isId ? target.slice(1).trim() : null,
        label: labelSource !== '' ? labelSource : isId ? target.slice(1).trim() : target,
        start: baseOffset + position,
        end: baseOffset + position + raw.length,
      },
    })
    return placeholder
  })
}

export interface RenderMarkupOptions {
  /** Резолвер по локальной базе кампании. */
  resolver?: LinkResolver
  /** Дополнительный класс контейнера ссылок. */
  linkClass?: string
}

export interface RenderedWikiLink {
  id: string
  label: string
  resolved: boolean
  /** Имя для создания объекта, если ссылка неразрешена. */
  name: string
}

/**
 * Готовит HTML описания. Возвращает и HTML, и список ссылок — по нему интерфейс
 * понимает, какие ссылки «битые» и что предложить создать.
 */
export function renderMarkup(
  text: string,
  options: RenderMarkupOptions = {},
): { html: string; links: RenderedWikiLink[] } {
  const resolver = options.resolver ?? emptyLinkResolver
  const source = text ?? ''
  if (source.trim() === '') {
    return { html: '', links: [] }
  }
  const extracted = extractWikiLinks(source)
  const rendered = md.render(extracted.text)
  const wikiLinks: RenderedWikiLink[] = []
  const extraClass = options.linkClass ? ` ${options.linkClass}` : ''

  let html = rendered
  for (const { placeholder, match } of extracted.links) {
    const resolved = resolver.resolve(match)
    const label = escapeHtml(resolved.label)
    const href = resolved.resolved ? `#/wiki/${encodeURIComponent(resolved.resolvedId ?? '')}` : '#'
    const classes = resolved.resolved
      ? `wikilink${extraClass}`
      : `wikilink wikilink--broken${extraClass}`
    const title = resolved.resolved
      ? 'Открыть карточку'
      : 'Объект не найден — нажмите, чтобы создать'
    const anchor =
      `<a class="${classes}" href="${href}" data-wiki-id="${escapeHtml(resolved.resolvedId ?? '')}"` +
      ` data-wiki-name="${escapeHtml(resolved.name ?? resolved.label)}"` +
      ` data-wiki-resolved="${resolved.resolved ? '1' : '0'}" title="${title}">${label}</a>`
    html = html.split(placeholder).join(anchor)
    wikiLinks.push({
      id: resolved.resolvedId ?? '',
      label: resolved.label,
      resolved: resolved.resolved,
      name: resolved.name ?? resolved.label,
    })
  }

  return { html: sanitize(html), links: wikiLinks }
}

function sanitize(html: string): string {
  if (typeof window === 'undefined') return html
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['data-wiki-id', 'data-wiki-name', 'data-wiki-resolved', 'target', 'rel'],
    ALLOWED_URI_REGEXP: /^(?:#|https?:|mailto:|tel:)/i,
  })
}

/** Простая очистка HTML без markdown — для подсвеченных фрагментов поиска. */
export function sanitizeSnippet(html: string): string {
  if (typeof window === 'undefined') return html
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['b', 'strong', 'em', 'br'], ALLOWED_ATTR: [] })
}
