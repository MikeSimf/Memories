package ru.dndmemory.ui.common;

import android.graphics.PorterDuff;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.dndmemory.R;

/**
 * Значки классов — нарезанные из исходной картинки монохромные PNG.
 * Кружок показывается только у героя и персонажа с известным классом.
 *
 * <p>Картинки лежат в {@code drawable-nodpi}, поэтому не зависят от плотности
 * экрана; цвет задаётся тонированием по альфе ({@link PorterDuff.Mode#SRC_IN}),
 * так что значок одинаково выглядит в светлой и тёмной теме.</p>
 */
public final class ClassIcons {

    private static final Map<String, Integer> ICONS = new HashMap<>();

    static {
        put(new String[]{"barbarian", "варвар"}, R.drawable.class_barbarian);
        put(new String[]{"bard", "бард"}, R.drawable.class_bard);
        put(new String[]{"cleric", "жрец"}, R.drawable.class_cleric);
        put(new String[]{"druid", "друид"}, R.drawable.class_druid);
        put(new String[]{"fighter", "воин"}, R.drawable.class_fighter);
        put(new String[]{"monk", "монах"}, R.drawable.class_monk);
        put(new String[]{"paladin", "паладин"}, R.drawable.class_paladin);
        put(new String[]{"ranger", "следопыт"}, R.drawable.class_ranger);
        put(new String[]{"rogue", "плут"}, R.drawable.class_rogue);
        put(new String[]{"sorcerer", "чародей"}, R.drawable.class_sorcerer);
        put(new String[]{"warlock", "колдун"}, R.drawable.class_warlock);
        put(new String[]{"wizard", "волшебник"}, R.drawable.class_wizard);
        put(new String[]{"artificer", "изобретатель"}, R.drawable.class_artificer);
    }

    private ClassIcons() {
    }

    /** Задан ли класс из справочника: «Неизвестно» и «Без класса» — нет. */
    public static boolean isKnown(@Nullable String klass) {
        return klass != null && ICONS.containsKey(normalize(klass));
    }

    public static void apply(@NonNull ImageView view, @Nullable String klass, @ColorInt int color) {
        Integer res = ICONS.get(normalize(klass));
        if (res == null) {
            return;
        }
        view.setImageResource(res);
        view.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static void put(@NonNull String[] aliases, int res) {
        for (String alias : aliases) {
            ICONS.put(normalize(alias), res);
        }
    }
}
