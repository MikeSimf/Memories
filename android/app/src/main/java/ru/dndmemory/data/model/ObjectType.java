package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/**
 * Тип объекта в журнале изменений (docs/sync.md, раздел 1).
 * Общего {@code ENTITY} больше нет: у каждого класса объектов своё значение.
 */
public enum ObjectType {
    HERO,
    CHARACTER,
    LOCATION,
    ITEM,
    RETELLING,
    CAMPAIGN,
    MEMBER,
    /** Фото-материалы кампании. */
    MATERIAL;

    @Nullable
    public static ObjectType from(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        for (ObjectType t : values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    public static ObjectType from(@Nullable EntityType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case HERO:
                return HERO;
            case CHARACTER:
                return CHARACTER;
            case LOCATION:
                return LOCATION;
            case ITEM:
                return ITEM;
            default:
                return null;
        }
    }

    /** Класс объекта для записей журнала о героях, персонажах, локациях и предметах. */
    @Nullable
    public EntityType entityType() {
        switch (this) {
            case HERO:
                return EntityType.HERO;
            case CHARACTER:
                return EntityType.CHARACTER;
            case LOCATION:
                return EntityType.LOCATION;
            case ITEM:
                return EntityType.ITEM;
            default:
                return null;
        }
    }
}
