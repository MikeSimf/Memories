package ru.dndmemory.ui.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.model.EntityType;

/**
 * Описание полей {@code attrs} по типам объектов (docs/api.md, раздел 6).
 * По этому описанию строится редактор характеристик и строка «Характеристики»
 * в карточке — отдельный код на каждый тип не нужен.
 */
public final class Attrs {

    public enum Kind {
        /** Свободный текст. */
        TEXT,
        /** Число. */
        NUMBER,
        /** Значение из фиксированного списка. */
        ENUM,
        /** Ссылка на другой объект кампании. */
        ENTITY_REF
    }

    public static final class Field {

        public final String key;
        public final int labelRes;
        public final Kind kind;
        /** Для ENUM: значения и их подписи. */
        public final String[] values;
        public final int[] valueLabels;
        /** Для ENTITY_REF: тип объекта-цели. */
        public final EntityType refType;

        Field(String key, int labelRes, Kind kind, String[] values, int[] valueLabels, EntityType refType) {
            this.key = key;
            this.labelRes = labelRes;
            this.kind = kind;
            this.values = values;
            this.valueLabels = valueLabels;
            this.refType = refType;
        }
    }

    private static final List<Field> HERO_FIELDS = new ArrayList<>();
    private static final List<Field> NPC_FIELDS = new ArrayList<>();
    private static final List<Field> LOCATION_FIELDS = new ArrayList<>();
    private static final List<Field> ITEM_FIELDS = new ArrayList<>();

    static {
        Collections.addAll(HERO_FIELDS,
                new Field("playerName", R.string.attr_player_name, Kind.TEXT, null, null, null),
                new Field("race", R.string.attr_race, Kind.TEXT, null, null, null),
                new Field("klass", R.string.attr_klass, Kind.TEXT, null, null, null),
                new Field("level", R.string.attr_level, Kind.NUMBER, null, null, null),
                new Field("hp", R.string.attr_hp, Kind.NUMBER, null, null, null),
                new Field("ac", R.string.attr_ac, Kind.NUMBER, null, null, null),
                new Field("status", R.string.attr_status, Kind.ENUM,
                        new String[]{"alive", "dead", "retired"},
                        new int[]{R.string.status_alive, R.string.status_dead, R.string.status_retired},
                        null));

        Collections.addAll(NPC_FIELDS,
                new Field("faction", R.string.attr_faction, Kind.TEXT, null, null, null),
                new Field("status", R.string.attr_status, Kind.ENUM,
                        new String[]{"alive", "dead", "unknown"},
                        new int[]{R.string.status_alive, R.string.status_dead, R.string.status_unknown},
                        null),
                new Field("locationEntityId", R.string.attr_location, Kind.ENTITY_REF,
                        null, null, EntityType.LOCATION));

        Collections.addAll(LOCATION_FIELDS,
                new Field("region", R.string.attr_region, Kind.TEXT, null, null, null),
                new Field("kind", R.string.attr_kind, Kind.ENUM,
                        new String[]{"city", "dungeon", "wild", "building"},
                        new int[]{R.string.kind_city, R.string.kind_dungeon,
                                R.string.kind_wild, R.string.kind_building},
                        null),
                new Field("parentEntityId", R.string.attr_parent, Kind.ENTITY_REF,
                        null, null, EntityType.LOCATION));

        Collections.addAll(ITEM_FIELDS,
                new Field("kind", R.string.attr_kind, Kind.ENUM,
                        new String[]{"weapon", "armor", "potion", "treasure", "quest"},
                        new int[]{R.string.kind_weapon, R.string.kind_armor, R.string.kind_potion,
                                R.string.kind_treasure, R.string.kind_quest},
                        null),
                new Field("ownerEntityId", R.string.attr_owner, Kind.ENTITY_REF, null, null, EntityType.HERO),
                new Field("locationEntityId", R.string.attr_location, Kind.ENTITY_REF, null, null, EntityType.LOCATION),
                new Field("quantity", R.string.attr_quantity, Kind.NUMBER, null, null, null),
                new Field("rarity", R.string.attr_rarity, Kind.TEXT, null, null, null));
    }

    private Attrs() {
    }

    public static List<Field> fieldsFor(EntityType type) {
        if (type == null) {
            return NPC_FIELDS;
        }
        switch (type) {
            case HERO:
                return HERO_FIELDS;
            case LOCATION:
                return LOCATION_FIELDS;
            case ITEM:
                return ITEM_FIELDS;
            case NPC:
            default:
                return NPC_FIELDS;
        }
    }
}
