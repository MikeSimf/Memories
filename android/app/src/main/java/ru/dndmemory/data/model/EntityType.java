package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/** Тип объекта кампании (docs/api.md, раздел 2). */
public enum EntityType {
    HERO,
    NPC,
    LOCATION,
    ITEM;

    public static final List<EntityType> ALL = Arrays.asList(HERO, NPC, LOCATION, ITEM);

    /** Значения для запроса {@code /search?types=...}. */
    public static final String SEARCH_TYPES = "HERO,NPC,LOCATION,ITEM,RETELLING";

    @Nullable
    public static EntityType from(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        for (EntityType t : values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        return null;
    }

    public static EntityType fromOrNpc(@Nullable String raw) {
        EntityType t = from(raw);
        return t == null ? NPC : t;
    }

    public boolean isHero() {
        return this == HERO;
    }

    /** Герои партии правятся отдельным правом {@code hero.write}. */
    public boolean requiresHeroPermission() {
        return this == HERO;
    }
}
