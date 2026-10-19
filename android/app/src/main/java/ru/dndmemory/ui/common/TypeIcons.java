package ru.dndmemory.ui.common;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Иконки рас и типов локаций/предметов — тот же набор глифов, что и на сайте.
 *
 * <p>Глиф рисуется обводкой в координатах 24×24: у каждой расы и каждого типа
 * свой рисунок, поэтому значок читается до подписи. Неизвестное значение
 * получает вопросительный знак, а не случайную картинку.</p>
 */
public final class TypeIcons {

    private static final Map<String, String> RACES = new HashMap<>();
    private static final Map<String, String> LOCATIONS = new HashMap<>();
    private static final Map<String, String> ITEMS = new HashMap<>();

    static {
        put(RACES, new String[]{"human", "человек"}, "M12 4.8a3 3 0 1 0 0 6a3 3 0 1 0 0-6M4.8 20c0-3.8 3.2-6.4 7.2-6.4s7.2 2.6 7.2 6.4");
        put(RACES, new String[]{"half_elf", "полуэльф"}, "M12 5.2a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6M14 6.2c1.2 0 2.4-.6 3.2-1.6.2 1.8-.6 3.4-2.2 4M5 20c0-3.4 3-5.8 7-5.8s7 2.4 7 5.8");
        put(RACES, new String[]{"elf", "эльф"}, "M12 3.2c-5.4 2.6-8 6.4-8 10.6 0 3.2 2.2 5.4 5 5.4 4.6 0 7-6 7-13.4 0-1.4-1.6-2.6-4-2.6zM7.6 18.4C9 13 11 9 14.4 6.4");
        put(RACES, new String[]{"dwarf", "дварф"}, "M7.4 7.4a4.6 4.6 0 0 1 9.2 0v1.8H7.4zM6.6 9.2h10.8M12 11v1.6M8.8 11.4c0 3.8 1 6.2 3.2 7.8 2.2-1.6 3.2-4 3.2-7.8M5.4 20c0-2.8 2.8-4.8 6.6-4.8s6.6 2 6.6 4.8");
        put(RACES, new String[]{"halfling", "полурослик"}, "M12 6.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2M7.4 19.4c0-3 2-5 4.6-5s4.6 2 4.6 5M4.6 19.8h4M15.4 19.8h4");
        put(RACES, new String[]{"gnome", "гном"}, "M12 2.8l3.8 6.4H8.2zM12 9.2a2.4 2.4 0 1 0 0 4.8a2.4 2.4 0 1 0 0-4.8M7.4 20c0-2.8 2-4.8 4.6-4.8s4.6 2 4.6 4.8");
        put(RACES, new String[]{"half_orc", "полуорк"}, "M12 5.4a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6M9 12.6v-2.4M5 20c0-3.2 3-5.6 7-5.6s7 2.4 7 5.6");
        put(RACES, new String[]{"orc", "орк"}, "M12 5.6a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6M8.2 8.8h7.6M8.8 12.8v-2.6M15.2 12.8v-2.6M8.6 12.2c1 1.8 2.1 2.6 3.4 2.6s2.4-.8 3.4-2.6M5 20c0-3.2 3-5.6 7-5.6s7 2.4 7 5.6");
        put(RACES, new String[]{"tiefling", "тифлинг"}, "M12 7.4a3 3 0 1 0 0 6a3 3 0 1 0 0-6M8.8 7C7 5.8 6.4 3.8 7.2 2.4c1.8.4 3.2 1.8 3.8 3.4M15.2 7c1.8-1.2 2.4-3.2 1.6-4.6-1.8.4-3.2 1.8-3.8 3.4M6 20c0-3.2 2.7-5.6 6-5.6s6 2.4 6 5.6");
        put(RACES, new String[]{"dragonborn", "драконорождённый", "драконорожденный"}, "M3.4 12c2.6-4 5.6-6 8.6-6s6 2 8.6 6c-2.6 4-5.6 6-8.6 6s-6-2-8.6-6zM12 8.8c-1.5 2-1.5 4.4 0 6.4 1.5-2 1.5-4.4 0-6.4zM4.2 7.4 2.4 4.8M19.8 7.4l1.8-2.6");
        put(RACES, new String[]{"goblin", "гоблин"}, "M12 7.2a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6M9.2 8.2 4.6 6.4c-.6 2.2.8 4.4 3.2 5.2M14.8 8.2l4.6-1.8c.6 2.2-.8 4.4-3.2 5.2M6 20c0-3 2.7-5.4 6-5.4s6 2.4 6 5.4");
        put(RACES, new String[]{"kobold", "кобольд"}, "M3.6 8.6c0-3 2.4-5.4 5.4-5.4s5.4 2.4 5.4 5.4M7.2 14c0-3 2.4-5.4 5.4-5.4s5.4 2.4 5.4 5.4M3.6 19.4c0-3 2.4-5.4 5.4-5.4s5.4 2.4 5.4 5.4");
        put(RACES, new String[]{"drow", "дроу"}, "M12 3.8c-3.2 0-5.2 2.2-5.2 5.2v2.8c0 2 1 3.4 2.6 4L12 21l2.6-5.2c1.6-.6 2.6-2 2.6-4V9c0-3-2-5.2-5.2-5.2zM9.8 9.4h.01M14.2 9.4h.01M6.4 6.4 4.2 4.2M17.6 6.4l2.2-2.2");
        put(RACES, new String[]{"aasimar", "аасимар"}, "M12 6.6a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2M8.2 3.4h7.6M5.2 20c0-3.2 2.8-5.6 6.8-5.6s6.8 2.4 6.8 5.6M6 12.2 2.8 9.8M18 12.2l3.2-2.4");
        put(RACES, new String[]{"genasi", "дженази"}, "M12 2.8c1 3.4 4.4 4.8 4.4 8.6a4.4 4.4 0 0 1-8.8 0c0-2 .8-3.4 2-4.6.4 1.6 1.2 2.4 2.4 2.8C12.8 7.6 11.6 5.2 12 2.8zM10.2 14.4c-.6 1-1 1.8-1 2.8a2.4 2.4 0 0 0 4.8 0c0-1.4-1-2.4-2.4-3.4");
        put(RACES, new String[]{"goliath", "голиаф"}, "M12 3.6a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4M5 20c0-3 2-5.4 4.6-6.4L12 8.4l2.4 5.2C17 14.6 19 17 19 20z");
        put(RACES, new String[]{"centaur", "кентавр"}, "M4 15c0-2 1.6-3.6 3.6-3.6h4.8c1.4 0 2.6.8 3.2 2h3.4c1.4 0 2.6 1.2 2.6 2.6 0 1-.6 1.8-1.4 2.2M15 13.4 17.4 8l2.4 1.2-1 3.2M6 16v4M9.4 16v4M17 16v4M19.8 16v4M4 13.6c-1.2-.6-1.8-1.8-1.6-3");
        put(RACES, new String[]{"unknown", "неизвестно", "без расы"}, "M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2M9.4 9.2a2.6 2.6 0 1 1 4.2 2.2c-.9.6-1.6 1.2-1.6 2.4M12 17.2h.01");
        put(LOCATIONS, new String[]{"unknown", "неизвестно"}, "M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2M9.4 9.2a2.6 2.6 0 1 1 4.2 2.2c-.9.6-1.6 1.2-1.6 2.4M12 17.2h.01");
        put(LOCATIONS, new String[]{"city", "город"}, "M3.4 20V9.6h4V6h3.2v3.6h3.2V4.4h3.2V9.6h4V20zM6.4 12.4h1.2M6.4 15.6h1.2M16.4 12.4h1.2M16.4 15.6h1.2M11.6 13.2h1.2v2.4h-1.2z");
        put(LOCATIONS, new String[]{"village", "деревня"}, "M3.4 20v-6.6L12 6l8.6 7.4V20zM9.4 20v-4.6h5.2V20");
        put(LOCATIONS, new String[]{"forest", "лес"}, "M12 2.8l4 6.4h-2.4l3.2 5H7.2l3.2-5H8zM12 14.2V21");
        put(LOCATIONS, new String[]{"plain", "равнина"}, "M2.6 18.4h18.8M4 18.4c3-2.4 5.6-2.4 8 0M12 18.4c3-2.4 5.6-2.4 8 0M12 3.4a3.4 3.4 0 1 0 0 6.8a3.4 3.4 0 1 0 0-6.8M12 2v2M12 9.6v2M8.6 5.8 7 4.6M15.4 5.8 17 4.6");
        put(LOCATIONS, new String[]{"steppe", "степь"}, "M2.6 19h18.8M5 19c0-2.6 1-4.8 2.4-6.4M9 19c0-2 1-4 2.4-5.6M13.4 19c0-2.8 1.2-5.2 2.8-6.8M18 19c0-1.8.8-3.4 2-4.6");
        put(LOCATIONS, new String[]{"crossroads", "перекрёсток", "перекресток"}, "M12 2.6v18.8M2.6 12h18.8M12 2.6 9.4 5.4M12 2.6l2.6 2.8M21.4 12l-2.8-2.6M21.4 12l-2.8 2.6M12 21.4l-2.6-2.8M12 21.4l2.6-2.8M2.6 12l2.8-2.6M2.6 12l2.8 2.6");
        put(LOCATIONS, new String[]{"otherworld", "другое измерение"}, "M12 2.8a9.2 9.2 0 1 0 0 18.4a9.2 9.2 0 1 0 0-18.4M12 6.2a5.8 5.8 0 1 0 0 11.6a5.8 5.8 0 1 0 0-11.6M12 9.8a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4");
        put(LOCATIONS, new String[]{"mountains", "горы"}, "M2.4 19.6 8.6 7.2l3.2 6 2.2-3.6 7.6 10zM8.6 7.2l1.6 3.2M5.6 13.2h2.6");
        put(LOCATIONS, new String[]{"cave", "пещера"}, "M3 20V12C3 7 7 3.4 12 3.4S21 7 21 12v8zM8.2 20v-4.6a3.8 3.8 0 0 1 7.6 0V20");
        put(LOCATIONS, new String[]{"dungeon", "подземелье"}, "M4 20V4h16v16zM4 8h16M4 12h16M4 16h16M8 4v16M12 4v16M16 4v16");
        put(LOCATIONS, new String[]{"ruins", "руины"}, "M3.4 20V8.6h3V20M9.4 20V5.4h3.2V20M15.8 20v-7.4h3V20M2.6 20h18.8M3.4 8.6l3-2.4M9.4 5.4l3.2-2");
        put(LOCATIONS, new String[]{"tower", "башня"}, "M8 20V8l4-5 4 5v12zM6 20h12M8 8h8M8 12h8M8 16h8");
        put(LOCATIONS, new String[]{"swamp", "болото"}, "M2.6 19.4h18.8M6 19.4v-6M6 13.4c-1.8 0-3-1.4-3-3M6 15.4c1.8 0 3-1.4 3-3M11 19.4v-8M11 11.4c-1.8 0-3-1.4-3-3M11 14c1.8 0 3-1.4 3-3M16.4 19.4v-5M16.4 14.4c-1.6 0-2.6-1.2-2.6-2.6");
        put(LOCATIONS, new String[]{"coast", "побережье"}, "M2.6 16.6c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8M12 3.4v10M8.4 7l3.6-3.6L15.6 7");
        put(LOCATIONS, new String[]{"sea", "море"}, "M2.6 9.4c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8M2.6 14.2c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8M2.6 19c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8");
        put(LOCATIONS, new String[]{"desert", "пустыня"}, "M2.6 19h18.8M4 19c0-3.4 2.4-6.2 5.6-6.2 2.2 0 3.6 1 4.4 2.4M9 19c0-2.6 1.8-4.8 4.2-4.8 2 0 3.4 1 4 2.4M15.4 19c0-2 1.4-3.6 3.2-3.6M17 4.4a3 3 0 1 0 0 6a3 3 0 1 0 0-6M17 2v1.4M17 11.4V13M13.4 7.4h1.4M19.2 7.4h1.4M14.6 5l-1-1M19.4 5l1-1");
        put(ITEMS, new String[]{"unknown", "неизвестно"}, "M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2M9.4 9.2a2.6 2.6 0 1 1 4.2 2.2c-.9.6-1.6 1.2-1.6 2.4M12 17.2h.01");
        put(ITEMS, new String[]{"weapon", "оружие"}, "M12 2.6v13M8.6 5.4 12 2.4l3.4 3M8.8 15.6h6.4M10.4 15.6l-2.2 5.8M13.6 15.6l2.2 5.8M9 12.4h6");
        put(ITEMS, new String[]{"armor", "броня"}, "M12 3 20 5.8v6c0 4.4-3.2 7.4-8 9.2-4.8-1.8-8-4.8-8-9.2v-6zM12 7.4v9.2M8.6 10.6h6.8");
        put(ITEMS, new String[]{"artifact", "артефакт"}, "M12 2.8 20.4 8.6 17.2 19.4H6.8L3.6 8.6zM3.6 8.6h16.8M12 2.8v16.6M8.2 8.6 12 19.4M15.8 8.6 12 19.4");
        put(ITEMS, new String[]{"jewelry", "драгоценность"}, "M12 8.6a7 7 0 1 0 0 14a7 7 0 1 0 0-14M12 5.4l-2 3.2h4zM12 3.2l2 2.2M12 3.2 10 5.4");
        put(ITEMS, new String[]{"potion", "зелье"}, "M9.4 3h5.2v3l3 4.4c1 1.4 1.6 3 1.6 4.8 0 3.4-2.6 6-6 6s-6-2.6-6-6c0-1.8.6-3.4 1.6-4.8l3-4.4zM8.2 14.4c1.6 1 2.8 1 4 0s2.4-1 3.6 0M9.4 3h5.2");
        put(ITEMS, new String[]{"scroll", "свиток"}, "M6 4h11.4c1.2 0 2.2 1 2.2 2.2v11.6c0 1.2-1 2.2-2.2 2.2H6M6 4c-1.2 0-2.2 1-2.2 2.2S4.8 8.4 6 8.4h2.2v11.4c0 1.2-1 2.2-2.2 2.2s-2.2-1-2.2-2.2M8.2 8.4h9M8.2 12h9M8.2 15.6h5.4");
        put(ITEMS, new String[]{"trophy", "трофей"}, "M7.4 3h9.2v6.4a4.6 4.6 0 0 1-9.2 0zM7.4 5H4.6v2.2c0 1.8 1.2 3.2 3 3.2M16.6 5h2.8v2.2c0 1.8-1.2 3.2-3 3.2M12 14v4M8.4 21h7.2M9.6 21c0-1.8 1-3 2.4-3s2.4 1.2 2.4 3");
        put(ITEMS, new String[]{"tool", "инструмент"}, "M14.6 2.8 21.2 9.4l-3 3-6.6-6.6zM11 8.4 3 16.4 4.6 18l7-7M5.6 3.4 9.4 7.2M3 4.8l3.6 3.6");
        put(ITEMS, new String[]{"book", "книга"}, "M4 4.4h6.2c1 0 1.8.8 1.8 1.8v13.4c0-1-.8-1.8-1.8-1.8H4zM20 4.4h-6.2c-1 0-1.8.8-1.8 1.8v13.4c0-1 .8-1.8 1.8-1.8H20z");
        put(ITEMS, new String[]{"transport", "транспорт"}, "M3 6h11v9H3zM14 9h4l3 4v2h-7zM7 18.6a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6M17 18.6a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2");
        put(ITEMS, new String[]{"treasure", "сокровище"}, "M3 10h18v9.4H3zM3 10C3 6.6 6 4 12 4s9 2.6 9 6M10 4v3M3 13.4h18M10.6 13.4h2.8v2.8h-2.8z");
        put(ITEMS, new String[]{"other", "другое"}, "M12 2.8 20.4 7.4v9.2L12 21.2 3.6 16.6V7.4zM3.6 7.4 12 12l8.4-4.6M12 12v9.2");
    }

    private TypeIcons() {
    }

    /** Глиф по классу объекта ({@code EntityType.name()}) и значению поля. */
    @NonNull
    public static String pathFor(@Nullable String kind, @Nullable String value) {
        Map<String, String> map;
        if ("LOCATION".equalsIgnoreCase(kind)) {
            map = LOCATIONS;
        } else if ("ITEM".equalsIgnoreCase(kind)) {
            map = ITEMS;
        } else if ("MATERIAL".equalsIgnoreCase(kind)) {
            map = ITEMS;
            value = "свиток";
        } else {
            map = RACES;
        }
        String key = normalize(value);
        String path = map.get(key);
        if (path != null) {
            return path;
        }
        return map.get("неизвестно");
    }

    /** Красит глиф в цвет типа и ставит его в {@code view}. */
    public static void apply(@NonNull ImageView view, @Nullable String kind,
                             @Nullable String value, @ColorInt int color) {
        String data = pathFor(kind, value);
        view.setColorFilter(null);
        view.setImageDrawable(new GlyphDrawable(data, color));
    }

    /** «Драконорождённый» и «драконорожденный» — одно значение. */
    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static void put(@NonNull Map<String, String> map, @NonNull String[] aliases,
                            @NonNull String path) {
        for (String alias : aliases) {
            map.put(normalize(alias), path);
        }
    }

    /** Обводка пути 24×24, растянутая на границы View без искажений. */
    private static final class GlyphDrawable extends Drawable {

        private final Path path;
        private final Paint paint;

        GlyphDrawable(@NonNull String data, @ColorInt int color) {
            path = PathParser.createPathFromPathData(data);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float size = Math.min(bounds.width(), bounds.height());
            if (size <= 0 || path == null) {
                return;
            }
            float scale = size / 24f;
            canvas.save();
            canvas.translate(bounds.left + (bounds.width() - size) / 2f,
                    bounds.top + (bounds.height() - size) / 2f);
            canvas.scale(scale, scale);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
