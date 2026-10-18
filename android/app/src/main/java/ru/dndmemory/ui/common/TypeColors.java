package ru.dndmemory.ui.common;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import ru.dndmemory.R;
import ru.dndmemory.data.model.EntityType;

/**
 * Цвет класса объекта: герой, персонаж, локация, предмет.
 *
 * <p>В списке типы различимы с первого взгляда, без чтения подписи. Тона
 * приглушённые и согласованы с пергаментной палитрой приложения: тёмная тема
 * переопределяет те же имена в {@code values-night}.
 */
public final class TypeColors {

    private TypeColors() {
    }

    /** Основной цвет типа: полоска в списке, подпись, рамка. */
    @ColorInt
    public static int of(@NonNull Context context, @NonNull EntityType type) {
        return ContextCompat.getColor(context, mainRes(type));
    }

    @ColorInt
    public static int of(@NonNull Context context, @Nullable String kind) {
        return of(context, EntityType.fromOrCharacter(kind));
    }

    /** Подложка под тип: тот же тон, сильно разбавленный. */
    @ColorInt
    public static int soft(@NonNull Context context, @NonNull EntityType type) {
        switch (type) {
            case HERO:
                return ContextCompat.getColor(context, R.color.type_hero_soft);
            case LOCATION:
                return ContextCompat.getColor(context, R.color.type_location_soft);
            case ITEM:
                return ContextCompat.getColor(context, R.color.type_item_soft);
            case CHARACTER:
            default:
                return ContextCompat.getColor(context, R.color.type_character_soft);
        }
    }

    @ColorInt
    public static int soft(@NonNull Context context, @Nullable String kind) {
        return soft(context, EntityType.fromOrCharacter(kind));
    }

    private static int mainRes(@NonNull EntityType type) {
        switch (type) {
            case HERO:
                return R.color.type_hero;
            case LOCATION:
                return R.color.type_location;
            case ITEM:
                return R.color.type_item;
            case CHARACTER:
            default:
                return R.color.type_character;
        }
    }
}
