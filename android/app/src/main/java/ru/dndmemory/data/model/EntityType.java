package ru.dndmemory.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Класс объекта кампании (docs/api.md, раздел 2): {@code HERO}, {@code CHARACTER},
 * {@code LOCATION}, {@code ITEM}. У каждого класса свой набор REST-эндпоинтов
 * и своё значение {@code objectType} в журнале синхронизации.
 */
public enum EntityType {
    HERO,
    CHARACTER,
    LOCATION,
    ITEM;

    public static final List<EntityType> ALL = Arrays.asList(HERO, CHARACTER, LOCATION, ITEM);

    /** Значения для запроса {@code /search?kinds=...}. */
    public static final String SEARCH_KINDS = "HERO,CHARACTER,LOCATION,ITEM,RETELLING";

    /**
     * Вкладка «Персонажи»: герои и персонажи (НПС) вместе, различаются пометкой
     * в списке (docs/redesign-v2.md, раздел 5).
     */
    public static final String CHARACTERS = "HERO,CHARACTER";

    /** Псевдо-класс поиска: пересказы живут отдельной таблицей. */
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

    /** Неизвестный или пустой класс трактуем как персонажа — самый безопасный вариант. */
    @NonNull
    public static EntityType fromOrCharacter(@Nullable String raw) {
        EntityType t = from(raw);
        return t == null ? CHARACTER : t;
    }

    /**
     * Разбор строки фильтра {@code kinds=HERO,CHARACTER} в список известных классов.
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

    /** Класс, которым создаётся объект кнопкой «Добавить» на этой вкладке. */
    @NonNull
    public static String createTypeFor(@Nullable String csv) {
        List<String> types = parseList(csv);
        if (types.isEmpty() || types.contains(CHARACTER.name())) {
            return CHARACTER.name();
        }
        return types.get(0);
    }

    public boolean isHero() {
        return this == HERO;
    }

    /** Героев ведёт только владелец кампании — право {@code hero.write}. */
    public boolean requiresHeroPermission() {
        return this == HERO;
    }
}
