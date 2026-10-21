/**
 * Значки классов — нарезанные из исходной картинки PNG с прозрачным фоном.
 * Лежат в `public/class-icons`, поэтому доступны по абсолютному пути.
 *
 * В интерфейсе картинка используется как CSS-маска и красится текущим цветом:
 * так она читается и в светлой, и в тёмной теме.
 */
const CLASSES: Array<{ keys: string[]; file: string }> = [
  { keys: ['barbarian', 'варвар'], file: 'barbarian' },
  { keys: ['bard', 'бард'], file: 'bard' },
  { keys: ['cleric', 'жрец'], file: 'cleric' },
  { keys: ['druid', 'друид'], file: 'druid' },
  { keys: ['fighter', 'воин'], file: 'fighter' },
  { keys: ['monk', 'монах'], file: 'monk' },
  { keys: ['paladin', 'паладин'], file: 'paladin' },
  { keys: ['ranger', 'следопыт'], file: 'ranger' },
  { keys: ['rogue', 'плут'], file: 'rogue' },
  { keys: ['sorcerer', 'чародей'], file: 'sorcerer' },
  { keys: ['warlock', 'колдун'], file: 'warlock' },
  { keys: ['wizard', 'волшебник'], file: 'wizard' },
  { keys: ['artificer', 'изобретатель'], file: 'artificer' },
]

function normalize(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase().replace(/ё/g, 'е')
}

const CACHE = new Map<string, string | null>()

/** URL значка класса или null, если класс не задан / неизвестен. */
export function classIconUrl(klass: string | null | undefined): string | null {
  const key = normalize(klass)
  if (CACHE.has(key)) return CACHE.get(key) ?? null
  const found = CLASSES.find((row) => row.keys.some((alias) => normalize(alias) === key))
  const url = found ? `/class-icons/${found.file}.png` : null
  CACHE.set(key, url)
  return url
}
