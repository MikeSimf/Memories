/**
 * Разметка `[[…]]` (`api.md` §6, `sync.md` §7).
 *
 * Поддерживаются форматы:
 *   `[[Имя объекта]]`
 *   `[[Имя|подпись]]`
 *   `[[#uuid|подпись]]`
 *
 * Имена разрешаются по локальной базе кампании без учёта регистра; при
 * неоднозначности выбирается первый по алфавиту — то же правило, что на сервере.
 */

export interface WikiLinkMatch {
  /** Исходный текст ссылки вместе со скобками. */
  raw: string
  /** Имя объекта или `null`, если ссылка задана по uuid. */
  name: string | null
  /** Явный uuid из формы `[[#uuid]]`. */
  targetId: string | null
  /** Подпись после `|` или имя/uuid, если подписи нет. */
  label: string
  start: number
  end: number
}

export interface ResolvedWikiLink extends WikiLinkMatch {
  /** Итоговый идентификатор объекта, если ссылку удалось разрешить. */
  resolvedId: string | null
  resolved: boolean
}

/** Ссылка вида `[[#uuid]]` — по идентификатору, минуя поиск по имени. */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** Состояние разбора ограждённых блоков кода. */
export interface FenceState {
  marker: string | null
}

/**
 * Учитывает блоки кода ``` / ~~~: возвращает `true`, если строка относится к
 * блоку (открывает, закрывает или находится внутри) — внутри кода ссылки не ищутся.
 */
export function trackFence(state: FenceState, line: string): boolean {
  const match = /^\s*(`{3,}|~{3,})/.exec(line)
  if (state.marker !== null) {
    if (match && match[1][0] === state.marker[0] && match[1].length >= state.marker.length) {
      state.marker = null
    }
    return true
  }
  if (match) {
    state.marker = match[1]
    return true
  }
  return false
}

function parseInner(inner: string, raw: string, start: number, end: number): WikiLinkMatch {
  const pipe = inner.indexOf('|')
  const target = (pipe === -1 ? inner : inner.slice(0, pipe)).trim()
  const labelSource = pipe === -1 ? '' : inner.slice(pipe + 1).trim()
  const isId = target.startsWith('#')
  const targetId = isId ? target.slice(1).trim() : null
  const name = isId ? null : target
  const label = labelSource !== '' ? labelSource : (targetId ?? name ?? '')
  return { raw, name, targetId, label, start, end }
}

/** Все ссылки `[[…]]` вне блоков и вставок кода. */
export function parseWikiLinks(text: string): WikiLinkMatch[] {
  const result: WikiLinkMatch[] = []
  const fence: FenceState = { marker: null }
  let offset = 0
  for (const line of text.split('\n')) {
    if (trackFence(fence, line)) {
      offset += line.length + 1
      continue
    }
    for (const segment of splitInlineCode(line)) {
      if (segment.isCode) continue
      const regex = /\[\[([^\]\n]+)\]\]/g
      let match = regex.exec(segment.text)
      while (match !== null) {
        const start = offset + segment.start + match.index
        result.push(parseInner(match[1], match[0], start, start + match[0].length))
        match = regex.exec(segment.text)
      }
    }
    offset += line.length + 1
  }
  return result
}

interface Segment {
  text: string
  start: number
  isCode: boolean
}

/** Делит строку на части внутри и вне вставок кода (`` `…` ``). */
function splitInlineCode(line: string): Segment[] {
  const segments: Segment[] = []
  const regex = /(`+)([^`]*)`+/g
  let cursor = 0
  let match = regex.exec(line)
  while (match !== null) {
    if (match.index > cursor) {
      segments.push({ text: line.slice(cursor, match.index), start: cursor, isCode: false })
    }
    segments.push({ text: match[0], start: match.index, isCode: true })
    cursor = match.index + match[0].length
    match = regex.exec(line)
  }
  if (cursor < line.length) {
    segments.push({ text: line.slice(cursor), start: cursor, isCode: false })
  }
  if (segments.length === 0) {
    segments.push({ text: line, start: 0, isCode: false })
  }
  return segments
}

/* ------------------------------------------------------------------ */
/* Разрешение ссылок по локальной базе                                 */
/* ------------------------------------------------------------------ */

export interface LinkTarget {
  id: string
  name: string
}

export interface LinkResolver {
  /** Разрешение по имени (регистр не важен) или по uuid. */
  resolve(match: WikiLinkMatch): ResolvedWikiLink
  /** Все объекты кампании — для подсказки при вводе. */
  targets(): LinkTarget[]
}

function normalizeName(value: string): string {
  return value.trim().toLocaleLowerCase('ru')
}

/** Резолвер по набору объектов кампании: сначала точное совпадение по имени,
 * затем первое по алфавиту среди совпавших без учёта регистра.
 */
export function createLinkResolver(targets: LinkTarget[]): LinkResolver {
  const byId = new Map<string, LinkTarget>()
  const byName = new Map<string, LinkTarget[]>()
  for (const target of targets) {
    byId.set(target.id, target)
    const key = normalizeName(target.name)
    const bucket = byName.get(key)
    if (bucket) {
      bucket.push(target)
    } else {
      byName.set(key, [target])
    }
  }
  const alphabetical = Array.from(byId.values()).sort((a, b) => a.name.localeCompare(b.name, 'ru'))

  return {
    targets: () => alphabetical,
    resolve(match: WikiLinkMatch): ResolvedWikiLink {
      if (match.targetId && UUID_RE.test(match.targetId)) {
        const known = byId.get(match.targetId)
        return { ...match, resolvedId: known ? known.id : match.targetId, resolved: true }
      }
      const name = match.targetId ?? match.name ?? ''
      const candidates = byName.get(normalizeName(name)) ?? []
      if (candidates.length === 0) {
        return { ...match, resolvedId: null, resolved: false }
      }
      const best = candidates
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name, 'ru'))[0]
      return { ...match, resolvedId: best.id, resolved: true }
    },
  }
}

/** Пустой резолвер: все ссылки считаются неразрешёнными. */
export const emptyLinkResolver: LinkResolver = {
  resolve: (match) => ({ ...match, resolvedId: null, resolved: false }),
  targets: () => [],
}

/**
 * Локальный аналог того, что делает сервер при сохранении объекта (`sync.md` §7):
 * собирает `links` из `summary` и `description`. Нужен, чтобы ссылки и «битые»
 * упоминания работали без сети.
 */
export function collectLinks(
  texts: Array<string | null | undefined>,
  resolver: LinkResolver,
): Array<{ targetId: string | null; label: string; resolved: boolean }> {
  const links: Array<{ targetId: string | null; label: string; resolved: boolean }> = []
  const seen = new Set<string>()
  for (const text of texts) {
    if (!text) continue
    for (const match of parseWikiLinks(text)) {
      const resolved = resolver.resolve(match)
      const key = `${resolved.resolvedId ?? ''}|${resolved.label}`
      if (seen.has(key)) continue
      seen.add(key)
      links.push({
        targetId: resolved.resolvedId,
        label: resolved.label,
        resolved: resolved.resolved,
      })
    }
  }
  return links
}
