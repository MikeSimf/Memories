package ru.dndmemory.domain;

/** Роль участника кампании. */
public enum MemberRole {
    /** Полные права как создатель сессии. */
    OWNER,
    /** Редактор как мастер. */
    EDITOR_MASTER,
    /** Редактор как герой. */
    EDITOR_HERO,
    /** Просмотр как гость. */
    VIEWER
}
