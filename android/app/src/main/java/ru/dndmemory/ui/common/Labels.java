package ru.dndmemory.ui.common;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.HeroItem;
import ru.dndmemory.data.model.MemberRole;
import ru.dndmemory.data.model.MemberStatus;
import ru.dndmemory.data.model.SyncStatus;

/** Преобразование перечислений и полей классов в человекочитаемые подписи. */
public final class Labels {

    private Labels() {
    }

    // --- Классы объектов ----------------------------------------------------

    @StringRes
    public static int entityType(@Nullable EntityType type) {
        if (type == null) {
            return R.string.entity_type_character;
        }
        switch (type) {
            case HERO:
                return R.string.entity_type_hero;
            case LOCATION:
                return R.string.entity_type_location;
            case ITEM:
                return R.string.entity_type_item;
            case CHARACTER:
            default:
                return R.string.entity_type_character;
        }
    }

    @DrawableRes
    public static int entityIcon(@Nullable String rawKind) {
        EntityType type = EntityType.from(rawKind);
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
            case CHARACTER:
            default:
                return R.drawable.ic_npc;
        }
    }

    public static CharSequence entityTypeText(Context context, @Nullable String rawKind) {
        EntityType type = EntityType.from(rawKind);
        if (type == null) {
            return context.getString(R.string.attr_not_set);
        }
        return context.getString(entityType(type));
    }

    /**
     * Вторая строка в списке объектов: у героя — раса, класс и игрок,
     * у персонажа — раса и краткое описание, у предмета — владелец.
     */
    @Nullable
    public static CharSequence entitySubtitle(Context context, @Nullable EntityRoom room) {
        if (room == null) {
            return null;
        }
        EntityType kind = EntityType.from(room.kind);
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case HERO:
                return join(room.race, room.klass, room.playerName);
            case CHARACTER:
                return join(room.race, room.shortDescription);
            case ITEM:
                return TextUtils.isEmpty(room.ownerName) ? null : room.ownerName;
            case LOCATION:
            default:
                return null;
        }
    }

    /** Строка «Предмет ×2 — заметка» для карточки героя. */
    @Nullable
    public static CharSequence heroItemText(Context context, @Nullable HeroItem item) {
        if (item == null || TextUtils.isEmpty(item.name)) {
            return null;
        }
        StringBuilder out = new StringBuilder(item.name);
        if (item.quantity != null && item.quantity > 1) {
            out.append(" ×").append(item.quantity);
        }
        if (!TextUtils.isEmpty(item.notes)) {
            out.append(" — ").append(item.notes);
        }
        return out.toString();
    }

    @Nullable
    private static CharSequence join(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                values.add(part);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return TextUtils.join(" · ", values);
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
}
