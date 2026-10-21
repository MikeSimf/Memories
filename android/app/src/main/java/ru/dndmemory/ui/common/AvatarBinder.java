package ru.dndmemory.ui.common;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import okhttp3.OkHttpClient;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.model.Attachment;

/**
 * Аватар объекта и значок класса.
 *
 * <p>Если у объекта загружена картинка — показываем её. Иначе рисуем глиф
 * расы (для героя и персонажа) или типа локации и предмета, окрашенный в цвет
 * этого значения: так тип читается до подписи. Значок класса — маленький
 * кружок с первой буквой класса поверх аватара.
 */
public final class AvatarBinder {

    private AvatarBinder() {
    }

    /** Кладёт картинку (или силуэт) в {@code avatar}. */
    public static void bind(@NonNull Context context, @NonNull ImageView avatar,
                            @NonNull EntityRoom entity) {
        bind(context, avatar, entity, null);
    }

    /**
     * Аватар в порядке важности: отдельный аватар, первое фото галереи, силуэт.
     * Картинка грузится авторизованным клиентом; без клиента остаётся силуэт.
     */
    public static void bind(@NonNull Context context, @NonNull ImageView avatar,
                            @NonNull EntityRoom entity, @Nullable OkHttpClient client) {
        EntityType kind = EntityType.fromOrCharacter(entity.kind);
        int color = colorFor(context, kind, entity);
        // Аватар — кружок, как и значок класса рядом.
        avatar.setBackground(circle(soft(color)));
        avatar.setClipToOutline(true);

        String fileId = coverFileId(entity);
        if (fileId != null && client != null) {
            avatar.setColorFilter(null);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setContentDescription(context.getString(R.string.entity_avatar));
            ViewUtils.show(avatar, true);
            ImageLoader.load(client, fileId, avatar);
            return;
        }
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        TypeIcons.apply(avatar, kind.name(), iconValue(kind, entity),
                entity.gender, color);
        avatar.setContentDescription(context.getString(typeLabel(kind)));
        ViewUtils.show(avatar, true);
    }

    /** Файл-аватар: отдельное поле, иначе первая картинка из галереи. */
    @Nullable
    private static String coverFileId(@NonNull EntityRoom entity) {
        if (entity.avatarFileId != null && !entity.avatarFileId.isEmpty()) {
            return entity.avatarFileId;
        }
        for (Attachment file : Mappers.files(entity.filesJson)) {
            if (file != null && file.isImage() && file.id != null) {
                return file.id;
            }
        }
        return null;
    }

    /**
     * Второй кружок рядом с аватаром: значок класса. Показывается только
     * у героя и персонажа с классом из справочника.
     */
    public static void bindClassIcon(@NonNull Context context, @NonNull ImageView icon,
                                     @NonNull EntityRoom entity) {
        EntityType kind = EntityType.fromOrCharacter(entity.kind);
        if ((kind != EntityType.HERO && kind != EntityType.CHARACTER)
                || !ClassIcons.isKnown(entity.klass)) {
            ViewUtils.show(icon, false);
            return;
        }
        int color = classColor(context, entity.klass);
        icon.setBackground(circle(soft(color)));
        icon.setClipToOutline(true);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ClassIcons.apply(icon, entity.klass, color);
        icon.setContentDescription(context.getString(R.string.attr_klass) + ": " + entity.klass);
        ViewUtils.show(icon, true);
    }

    /** Круглая подложка нужного тона. */
    @NonNull
    private static android.graphics.drawable.GradientDrawable circle(@ColorInt int color) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }

    /** Значение, по которому выбирается глиф: раса или тип. */
    @Nullable
    private static String iconValue(@NonNull EntityType kind, @NonNull EntityRoom entity) {
        if (kind == EntityType.LOCATION) {
            return entity.locationType;
        }
        if (kind == EntityType.ITEM) {
            return entity.itemType;
        }
        return entity.race;
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
    private static int soft(@ColorInt int color) {
        return (color & 0x00FFFFFF) | 0x33000000;
    }
}
