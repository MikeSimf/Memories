package ru.dndmemory.ui.common;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import ru.dndmemory.R;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.MemberRole;
import ru.dndmemory.data.model.MemberStatus;
import ru.dndmemory.data.model.SyncStatus;

/** Преобразование перечислений и ключей attrs в человекочитаемые подписи. */
public final class Labels {

    private Labels() {
    }

    // --- Типы объектов ------------------------------------------------------

    @StringRes
    public static int entityType(@Nullable EntityType type) {
        if (type == null) {
            return R.string.entity_type_npc;
        }
        switch (type) {
            case HERO:
                return R.string.entity_type_hero;
            case LOCATION:
                return R.string.entity_type_location;
            case ITEM:
                return R.string.entity_type_item;
            case NPC:
            default:
                return R.string.entity_type_npc;
        }
    }

    @DrawableRes
    public static int entityIcon(@Nullable String rawType) {
        EntityType type = EntityType.from(rawType);
        if (type == null) {
            return R.drawable.ic_npc;
        }
        switch (type) {
            case HERO:
                return R.drawable.ic_hero;
            case LOCATION:
                return R.drawable.ic_location;
            case ITEM:
                return R.drawable.ic_item;
            case NPC:
            default:
                return R.drawable.ic_npc;
        }
    }

    public static CharSequence entityTypeText(Context context, @Nullable String rawType) {
        EntityType type = EntityType.from(rawType);
        if (type == null) {
            return context.getString(R.string.attr_not_set);
        }
        return context.getString(entityType(type));
    }

    // --- Роли и статусы участников -----------------------------------------

    @StringRes
    public static int role(@Nullable MemberRole role) {
        if (role == null) {
            return R.string.attr_not_set;
        }
        switch (role) {
            case OWNER:
                return R.string.role_owner;
            case EDITOR_MASTER:
                return R.string.role_editor_master;
            case EDITOR_HERO:
                return R.string.role_editor_hero;
            case VIEWER:
            default:
                return R.string.role_viewer;
        }
    }

    public static CharSequence roleText(Context context, @Nullable String raw) {
        MemberRole role = MemberRole.from(raw);
        if (role == null) {
            return context.getString(R.string.attr_not_set);
        }
        return context.getString(role(role));
    }

    @StringRes
    public static int memberStatus(@Nullable String raw) {
        MemberStatus status = MemberStatus.from(raw);
        if (status == null) {
            return R.string.attr_not_set;
        }
        switch (status) {
            case ACTIVE:
                return R.string.members_status_active;
            case INVITED:
                return R.string.members_status_invited;
            case REMOVED:
            default:
                return R.string.members_status_removed;
        }
    }

    // --- Синхронизация ------------------------------------------------------

    @StringRes
    public static int syncStatus(@Nullable SyncStatus status) {
        if (status == null) {
            return R.string.sync_status_idle;
        }
        switch (status) {
            case SYNCING:
                return R.string.sync_status_syncing;
            case OFFLINE:
                return R.string.sync_status_offline;
            case ERROR:
                return R.string.sync_status_error;
            case CONFLICT:
                return R.string.sync_status_conflict;
            case IDLE:
            default:
                return R.string.sync_status_idle;
        }
    }

    // --- Характеристики -----------------------------------------------------

    /** @return ресурс подписи или 0, если ключ неизвестен. */
    @StringRes
    public static int attrLabel(@Nullable String key) {
        if (key == null) {
            return 0;
        }
        switch (key) {
            case "playerName":
                return R.string.attr_player_name;
            case "race":
                return R.string.attr_race;
            case "klass":
                return R.string.attr_klass;
            case "level":
                return R.string.attr_level;
            case "hp":
                return R.string.attr_hp;
            case "ac":
                return R.string.attr_ac;
            case "status":
                return R.string.attr_status;
            case "faction":
                return R.string.attr_faction;
            case "locationEntityId":
                return R.string.attr_location;
            case "region":
                return R.string.attr_region;
            case "kind":
                return R.string.attr_kind;
            case "parentEntityId":
                return R.string.attr_parent;
            case "ownerEntityId":
                return R.string.attr_owner;
            case "quantity":
                return R.string.attr_quantity;
            case "rarity":
                return R.string.attr_rarity;
            default:
                return 0;
        }
    }

    public static CharSequence attrLabelText(Context context, @Nullable String key) {
        int res = attrLabel(key);
        if (res == 0) {
            return context.getString(R.string.attr_unknown, key == null ? "" : key);
        }
        return context.getString(res);
    }

    /** Значения-перечисления показываем по-русски, остальное — как есть. */
    public static CharSequence attrValue(Context context, @Nullable String key,
                                         @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return context.getString(R.string.attr_not_set);
        }
        if ("status".equals(key)) {
            switch (value) {
                case "alive":
                    return context.getString(R.string.status_alive);
                case "dead":
                    return context.getString(R.string.status_dead);
                case "retired":
                    return context.getString(R.string.status_retired);
                case "unknown":
                    return context.getString(R.string.status_unknown);
                default:
                    return value;
            }
        }
        if ("kind".equals(key)) {
            switch (value) {
                case "city":
                    return context.getString(R.string.kind_city);
                case "dungeon":
                    return context.getString(R.string.kind_dungeon);
                case "wild":
                    return context.getString(R.string.kind_wild);
                case "building":
                    return context.getString(R.string.kind_building);
                case "weapon":
                    return context.getString(R.string.kind_weapon);
                case "armor":
                    return context.getString(R.string.kind_armor);
                case "potion":
                    return context.getString(R.string.kind_potion);
                case "treasure":
                    return context.getString(R.string.kind_treasure);
                case "quest":
                    return context.getString(R.string.kind_quest);
                default:
                    return value;
            }
        }
        return value;
    }
}
