import type { EntityAttrs, EntityType } from '@/api/types'

/**
 * Поля `attrs` по типам объектов — таблица из `api.md` §6.
 * Сервер не отвергает лишние ключи, но перечисленные поля понимают все клиенты,
 * поэтому форма редактирования строится по этим описаниям.
 */

export type AttrFieldKind = 'text' | 'number' | 'select' | 'entity'

export interface AttrFieldOption {
  value: string
  label: string
}

export interface AttrField {
  key: string
  label: string
  kind: AttrFieldKind
  options?: readonly AttrFieldOption[]
  /** Типы объектов, из которых выбирается значение ссылочного поля. */
  entityTypes?: readonly EntityType[]
  placeholder?: string
  min?: number
  max?: number
}

const STATUS_ALIVE_DEAD_RETIRED: readonly AttrFieldOption[] = [
  { value: 'alive', label: 'Жив' },
  { value: 'dead', label: 'Погиб' },
  { value: 'retired', label: 'Отошёл от дел' },
]

const STATUS_ALIVE_DEAD_UNKNOWN: readonly AttrFieldOption[] = [
  { value: 'alive', label: 'Жив' },
  { value: 'dead', label: 'Мёртв' },
  { value: 'unknown', label: 'Неизвестно' },
]

const LOCATION_KINDS: readonly AttrFieldOption[] = [
  { value: 'city', label: 'Город' },
  { value: 'dungeon', label: 'Подземелье' },
  { value: 'wild', label: 'Дикая местность' },
  { value: 'building', label: 'Здание' },
]

const ITEM_KINDS: readonly AttrFieldOption[] = [
  { value: 'weapon', label: 'Оружие' },
  { value: 'armor', label: 'Доспех' },
  { value: 'potion', label: 'Зелье' },
  { value: 'treasure', label: 'Сокровище' },
  { value: 'quest', label: 'Сюжетный предмет' },
]

export const ATTR_SCHEMAS: Record<EntityType, readonly AttrField[]> = {
  HERO: [
    { key: 'playerName', label: 'Игрок', kind: 'text', placeholder: 'Имя игрока' },
    { key: 'race', label: 'Раса', kind: 'text', placeholder: 'Полуэльф' },
    { key: 'klass', label: 'Класс', kind: 'text', placeholder: 'Плут' },
    { key: 'level', label: 'Уровень', kind: 'number', min: 1, max: 30 },
    { key: 'hp', label: 'Хиты', kind: 'number', min: 0 },
    { key: 'ac', label: 'Класс доспеха', kind: 'number', min: 0 },
    { key: 'status', label: 'Состояние', kind: 'select', options: STATUS_ALIVE_DEAD_RETIRED },
  ],
  NPC: [
    { key: 'faction', label: 'Фракция', kind: 'text', placeholder: 'Совет Фандалина' },
    { key: 'status', label: 'Состояние', kind: 'select', options: STATUS_ALIVE_DEAD_UNKNOWN },
    { key: 'locationEntityId', label: 'Локация', kind: 'entity', entityTypes: ['LOCATION'] },
  ],
  LOCATION: [
    { key: 'region', label: 'Регион', kind: 'text', placeholder: 'Побережье Мечей' },
    { key: 'kind', label: 'Тип', kind: 'select', options: LOCATION_KINDS },
    { key: 'parentEntityId', label: 'Входит в', kind: 'entity', entityTypes: ['LOCATION'] },
  ],
  ITEM: [
    { key: 'kind', label: 'Тип', kind: 'select', options: ITEM_KINDS },
    { key: 'ownerEntityId', label: 'Владелец', kind: 'entity', entityTypes: ['HERO', 'NPC'] },
    { key: 'locationEntityId', label: 'Где находится', kind: 'entity', entityTypes: ['LOCATION'] },
    { key: 'quantity', label: 'Количество', kind: 'number', min: 0 },
    { key: 'rarity', label: 'Редкость', kind: 'text', placeholder: 'Необычный' },
  ],
}

export function attrFieldsFor(type: EntityType): readonly AttrField[] {
  return ATTR_SCHEMAS[type] ?? []
}

function isBlank(value: unknown): boolean {
  return value === undefined || value === null || value === '' || (typeof value === 'number' && Number.isNaN(value))
}

/**
 * Приводит `attrs` к виду, понятному серверу: числовые поля — числа,
 * пустые значения удаляются, лишние ключи сохраняются как есть.
 */
export function normalizeAttrs(type: EntityType, raw: EntityAttrs | undefined): EntityAttrs {
  const result: EntityAttrs = {}
  const fields = new Map(attrFieldsFor(type).map((field) => [field.key, field]))
  for (const [key, value] of Object.entries(raw ?? {})) {
    if (isBlank(value)) continue
    const field = fields.get(key)
    if (field?.kind === 'number') {
      const numeric = typeof value === 'number' ? value : Number(String(value).replace(',', '.'))
      if (Number.isNaN(numeric)) continue
      result[key] = numeric
      continue
    }
    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (trimmed === '') continue
      result[key] = trimmed
      continue
    }
    result[key] = value
  }
  return result
}

/** Подпись значения поля для карточки (например, `alive` → «Жив»). */
export function attrValueLabel(type: EntityType, key: string, value: unknown): string {
  const field = attrFieldsFor(type).find((item) => item.key === key)
  if (field?.kind === 'select' && typeof value === 'string') {
    const option = field.options?.find((item) => item.value === value)
    return option?.label ?? value
  }
  if (typeof value === 'boolean') return value ? 'Да' : 'Нет'
  return String(value)
}

/** Непустые значения `attrs` для показа в карточке. */
export function filledAttrs(type: EntityType, attrs: EntityAttrs | undefined): Array<{ key: string; label: string; value: string }> {
  const fields = attrFieldsFor(type)
  const known = new Set(fields.map((field) => field.key))
  const rows: Array<{ key: string; label: string; value: string }> = []
  for (const field of fields) {
    const value = attrs?.[field.key]
    if (isBlank(value)) continue
    rows.push({ key: field.key, label: field.label, value: attrValueLabel(type, field.key, value) })
  }
  for (const [key, value] of Object.entries(attrs ?? {})) {
    if (known.has(key) || isBlank(value)) continue
    rows.push({ key, label: key, value: String(value) })
  }
  return rows
}
