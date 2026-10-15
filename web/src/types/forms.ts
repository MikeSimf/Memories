import type { EntityAttrs, EntityType } from '@/api/types'

/** Значения формы объекта (герой, персонаж, локация, предмет). */
export interface EntityFormValues {
  type: EntityType
  name: string
  summary: string
  description: string
  attrs: EntityAttrs
  tags: string[]
  imageFileId: string | null
}

/** Значения формы пересказа. */
export interface RetellingFormValues {
  title: string
  body: string
  /** «От лица кого» — герой или персонаж кампании. */
  authorEntityId: string | null
  /** Дата сессии, `YYYY-MM-DD`. */
  sessionDate: string | null
}

export function emptyEntityForm(type: EntityType, name = ''): EntityFormValues {
  return {
    type,
    name,
    summary: '',
    description: '',
    attrs: {},
    tags: [],
    imageFileId: null,
  }
}

export function emptyRetellingForm(): RetellingFormValues {
  return { title: '', body: '', authorEntityId: null, sessionDate: null }
}
