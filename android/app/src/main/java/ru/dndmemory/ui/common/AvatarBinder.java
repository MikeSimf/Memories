package ru.dndmemory.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;

/**
 * Аватар объекта и значок класса.
 *
 * <p>Если у объекта загружена картинка — показываем её. Иначе рисуем силуэт
 * категории, окрашенный по расе (для героя и персонажа) или по типу локации
 * и предмета: так тип читается до подписи. Значок класса — маленький кружок
 * с первой буквой класса поверх аватара.
 */
public final class AvatarBinder {

    private AvatarBinder() {
    }

    /** Кладёт картинку (или силуэт) в {@code avatar}. */
    public static void bind(@NonNull Context context, @NonNull ImageView avatar,
                            @NonNull EntityRoom entity) {
        EntityType kind = EntityType.fromOrCharacter(entity.kind);
        int color = colorFor(context, kind, entity);
        avatar.setColorFilter(color);
        avatar.setImageResource(iconFor(kind));
        avatar.setContentDescription(context.getString(typeLabel(kind)));
    }

    /** Значок класса: буква и цвет. Если класса нет — скрываем. */
    public static void bindClassBadge(@NonNull Context context, @NonNull TextView badge,
                                      @NonNull EntityRoom entity) {
        EntityType kind = EntityType.fromOrCharacter(entity.kind);
        if (kind != EntityType.HERO && kind != EntityType.CHARACTER) {
            badge.setVisibility(View.GONE);
            return;
        }
        String klass = entity.klass == null ? "" : entity.klass.trim();
        if (klass.isEmpty()) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setVisibility(View.VISIBLE);
        badge.setText(initials(klass));
        int color = classColor(context, klass);
        badge.setTextColor(color);
        badge.setBackgroundTintList(ColorStateList.valueOf(soft(context, color)));
    }

    @DrawableRes
    private static int iconFor(@NonNull EntityType kind) {
        switch (kind) {
            case LOCATION:
                return R.drawable.ic_location;
            case ITEM:
                return R.drawable.ic_item;
            case CHARACTER:
                return R.drawable.ic_npc;
            case HERO:
            default:
                return R.drawable.ic_hero;
        }
    }

    private static int typeLabel(@NonNull EntityType kind) {
        switch (kind) {
            case LOCATION:
                return R.string.tab_locations;
            case ITEM:
                return R.string.tab_items;
            case CHARACTER:
                return R.string.tab_characters;
            default:
                return R.string.tab_characters;
        }
    }

    /**
     * Цвет силуэта: раса у людей, тип у локаций и предметов.
     * Неизвестное значение и «без …» дают нейтральный серый.
     */
    @ColorInt
    private static int colorFor(@NonNull Context context, @NonNull EntityType kind,
                                @NonNull EntityRoom entity) {
        String key;
        if (kind == EntityType.LOCATION) {
            key = entity.locationType;
        } else if (kind == EntityType.ITEM) {
            key = entity.itemType;
        } else {
            key = entity.race;
        }
        return palette(context, key);
    }

    /** Устойчивый цвет по строке: одинаковые расы и типы выглядят одинаково. */
    @ColorInt
    private static int palette(@NonNull Context context, @Nullable String key) {
        String value = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.startsWith("неизвест") || value.startsWith("без ")) {
            return ContextCompat.getColor(context, R.color.text_muted);
        }
        int[] colors = {
                R.color.type_hero,
                R.color.type_character,
                R.color.type_location,
                R.color.type_item,
                R.color.colorSecondary,
        };
        int index = Math.abs(value.hashCode()) % colors.length;
        return ContextCompat.getColor(context, colors[index]);
    }

    @ColorInt
    private static int classColor(@NonNull Context context, @NonNull String klass) {
        String value = klass.toLowerCase(Locale.ROOT);
        if (value.startsWith("неизвест") || value.startsWith("без ")) {
            return ContextCompat.getColor(context, R.color.text_muted);
        }
        int[] colors = {
                R.color.colorPrimary,
                R.color.type_hero,
                R.color.type_item,
                R.color.type_location,
                R.color.colorSecondary,
        };
        return ContextCompat.getColor(context, colors[Math.abs(value.hashCode()) % colors.length]);
    }

    /** Полупрозрачная подложка того же тона для кружка класса. */
    @ColorInt
    private static int soft(@NonNull Context context, @ColorInt int color) {
        return (color & 0x00FFFFFF) | 0x33000000;
    }

    /** Первая буква класса: «Воин» → «В», «Изобретатель» → «И». */
    @NonNull
    private static String initials(@NonNull String klass) {
        String trimmed = klass.trim();
        return trimmed.isEmpty() ? "" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
