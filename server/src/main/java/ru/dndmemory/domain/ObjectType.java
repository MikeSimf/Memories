package ru.dndmemory.domain;

/**
 * Тип объекта в журнале синхронизации. Каждый класс справочника — своё значение:
 * общего {@code ENTITY} больше нет, у каждого типа своя форма payload
 * (docs/redesign-v2.md, раздел 3).
 */
public enum ObjectType {
    HERO,
    CHARACTER,
    LOCATION,
    ITEM,
    RETELLING,
    CAMPAIGN,
    MEMBER,
    /** Фото-материалы кампании: карта, портрет, письмо. */
    MATERIAL
}
