/**
 * Значения форм объектов и пересказов, общие для экранов и сторов.
 *
 * Форма объекта — одна на все четыре класса (`api.md` §6): показываются только
 * поля своего класса, остальные остаются пустыми.
 */

/** Предмет в котомке героя в форме. */
export interface HeroItemFormValue {
  /** Идентификатор строки `hero_items`; у новых строк пусто. */
  id: string | null
  name: string
  /** Идентификатор предмета кампании, если название нашлось среди них. */
  itemId: string | null
  quantity: number | null
  notes: string
}

export interface ObjectFormValues {
  name: string
  description: string
  imageFileId: string | null
  /** Герой. */
  race: string
  klass: string
  playerUserId: string | null
  items: HeroItemFormValue[]
  /** Персонаж (НПС). */
  shortDescription: string
  /** Предмет: владелец «как написано в мире» и не более одной ссылки. */
  ownerName: string
  ownerHeroId: string | null
  ownerCharacterId: string | null
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

export function emptyHeroItem(): HeroItemFormValue {
  return { id: null, name: '', itemId: null, quantity: null, notes: '' }
}

export function emptyObjectForm(name = ''): ObjectFormValues {
  return {
    name,
    description: '',
    imageFileId: null,
    race: '',
    klass: '',
    playerUserId: null,
    items: [],
    shortDescription: '',
    ownerName: '',
    ownerHeroId: null,
    ownerCharacterId: null,
  }
}

export function emptyRetellingForm(): RetellingFormValues {
  return { title: '', body: '', authorEntityId: null, sessionDate: null }
}
