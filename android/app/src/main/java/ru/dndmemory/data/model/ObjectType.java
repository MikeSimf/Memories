package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/** Тип объекта в журнале изменений. */
public enum ObjectType {
    ENTITY,
    RETELLING,
    CAMPAIGN,
    MEMBER;

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
}
