/** Пол героя и персонажа: код хранится в базе, подпись показывается в интерфейсе. */
export const GENDERS = [
  { code: 'unknown', label: 'Не определён' },
  { code: 'female', label: 'Ж' },
  { code: 'male', label: 'М' },
] as const

export type GenderCode = (typeof GENDERS)[number]['code']

/** Неизвестное значение показываем как «Не определён», а не пустотой. */
export function genderLabel(code: string | null | undefined): string {
  return GENDERS.find((item) => item.code === code)?.label ?? 'Не определён'
}
