package ru.dndmemory.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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

    /** Вкладка «Персонажи»: герои и НПС вместе (docs/api.md, раздел 6). */
    public static final String CHARACTERS = "HERO,NPC";

    /** Регистрация в списке: 1 получатель, к которому применяется права {@code hero.write}. */
    public static final String RETELLING = "RETELLING";

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

    /**
     * Разбор строки фильтра {@code types=HERO,NPC} в список известных типов.
     * Пустая строка и мусор дают пустой список — это значит «без фильтра».
     */
    @NonNull
    public static List<String> parseList(@Nullable String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return out;
        }
        for (String part : csv.split(",")) {
            EntityType type = from(part);
            if (type != null && !out.contains(type.name())) {
                out.add(type.name());
            }
        }
        return out;
    }

    /** Тег вкладки: имя типа, которым создаётся объект кнопкой «Добавить». */
    @NonNull
    public static String createTypeFor(@Nullable String csv) {
        List<String> types = parseList(csv);
        if (types.isEmpty() || types.contains(NPC.name())) {
            return NPC.name();
        }
        return types.get(0);
    }

    public boolean isHero() {
        return this == HERO;
    }

    /** Герои партии правятся отдельным правом {@code hero.write}. */
    public boolean requiresHeroPermission() {
        return this == HERO;
    }
}
