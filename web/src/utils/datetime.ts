/** Работа с датами: ISO-8601 UTC с миллисекундами (`api.md` §1) и `YYYY-MM-DD` сессий. */

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const DATE_FORMATTER = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: 'long',
  year: 'numeric',
})

export function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** «15.10.2025, 14:00» */
export function formatDateTime(value: string | null | undefined): string {
  const date = parseDate(value)
  return date ? DATE_TIME_FORMATTER.format(date) : '—'
}

/** «15 октября 2025 г.» */
export function formatDate(value: string | null | undefined): string {
  const date = parseDate(value)
  return date ? DATE_FORMATTER.format(date) : '—'
}

/** «5 минут назад», «3 дня назад». */
export function formatRelative(value: string | null | undefined, now = Date.now()): string {
  const date = parseDate(value)
  if (!date) return '—'
  const diffMs = now - date.getTime()
  const future = diffMs < 0
  const seconds = Math.round(Math.abs(diffMs) / 1000)
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['second', 60],
    ['minute', 60],
    ['hour', 24],
    ['day', 30],
    ['month', 12],
    ['year', Number.POSITIVE_INFINITY],
  ]
  let amount = seconds
  let unit: Intl.RelativeTimeFormatUnit = 'second'
  for (const [nextUnit, divisor] of units) {
    if (amount < divisor) {
      unit = nextUnit
      break
    }
    amount = Math.round(amount / divisor)
    unit = nextUnit
  }
  const formatter = new Intl.RelativeTimeFormat('ru-RU', { numeric: 'auto' })
  return formatter.format(future ? amount : -amount, unit)
}

/** Значение для `<input type="date">`: `YYYY-MM-DD`. */
export function toDateInput(value: string | null | undefined): string {
  if (!value) return ''
  const isoDate = /^\d{4}-\d{2}-\d{2}/.exec(value)
  if (isoDate) return isoDate[0]
  const date = parseDate(value)
  if (!date) return ''
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function todayIso(): string {
  return toDateInput(new Date().toISOString())
}

export function isValidIsoDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value)
}
