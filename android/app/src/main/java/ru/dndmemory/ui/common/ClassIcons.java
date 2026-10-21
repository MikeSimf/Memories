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
 * Значки классов — те же глифы, что и на сайте. Кружок показывается только
 * у героя и персонажа с известным классом.
 */
public final class ClassIcons {

    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        put(new String[]{"barbarian", "варвар"}, "M14 2.8v18.4M14 5.4C10.6 5 7.6 6.6 5.8 9.4c1.8 2.8 4.8 4.4 8.2 4M14 7.6l3.2-1.4-.8 3.2");
        put(new String[]{"bard", "бард"}, "M7.4 3.2v17.2M7.4 3.2c6.6.8 9.2 5.6 9.2 10.6 0 4-2 6.6-5.2 7.2M7.4 8c3.2.4 5.6 2.8 5.6 6M9.6 5.4v12.4M11.8 5v11.2M14 5.4v9.4M7.4 20.4h5");
        put(new String[]{"cleric", "жрец"}, "M12 3.6v16.8M7.8 8.2h8.4M12 2v1.2M4.6 5.2l1.2 1.2M19.4 5.2l-1.2 1.2");
        put(new String[]{"druid", "друид"}, "M12 2.8c-4.6 2.8-6.8 6.4-6.8 9.8 0 3 2.2 5.4 5 5.4 4 0 6.2-4.8 5.2-11.8M12 2.8c1.2 4.6.2 8.2-2.2 10.6");
        put(new String[]{"fighter", "воин"}, "M4.2 3.8 15.6 15.2M19.8 3.8 8.4 15.2M4.2 3.8h3.4M19.8 3.8h-3.4M6.4 15.4 4 17.8l2.2 2.2 2.4-2.4M17.6 15.4 20 17.8l-2.2 2.2-2.4-2.4");
        put(new String[]{"monk", "монах"}, "M7.2 12.8V8a1.4 1.4 0 0 1 2.8 0v3.4M10 11.4V6.6a1.4 1.4 0 0 1 2.8 0v4.8M12.8 11.4V7.2a1.4 1.4 0 0 1 2.8 0v4.2M15.6 11.6V9.8a1.3 1.3 0 0 1 2.6 0v4.6c0 3.4-2.4 6-5.8 6s-5.6-2.6-5.6-6v-2.2");
        put(new String[]{"paladin", "паладин"}, "M12 2.6 20 5.6v6c0 4.6-3.2 7.8-8 9.6-4.8-1.8-8-5-8-9.6v-6zM12 7.4v9.6M9.6 9.8h4.8M10.4 17h3.2M12 17v2.2");
        put(new String[]{"ranger", "следопыт"}, "M6.6 2.8c6.2 2 9.4 5.8 9.4 9.2s-3.2 7.2-9.4 9.2M6.6 2.8v18.4M6.6 10v4M6.6 12h13M16.2 8.8 19.8 12l-3.6 3.2M9 12h3.4M9 10.4 7.4 12 9 13.6");
        put(new String[]{"rogue", "плут"}, "M8.4 6.2a3.2 3.2 0 1 0 0 6.4a3.2 3.2 0 1 0 0-6.4M11.4 9.4h8.8M17 9.4v3M20 9.4v2.2");
        put(new String[]{"sorcerer", "чародей"}, "M12 2.8c1 3.4 4.4 4.8 4.4 8.6a4.4 4.4 0 0 1-8.8 0c0-2 .8-3.4 2-4.6.4 1.6 1.2 2.4 2.4 2.8C12.8 7.6 11.6 5.2 12 2.8zM10.2 14.4c-.6 1-1 1.8-1 2.8a2.4 2.4 0 0 0 4.8 0c0-1.4-1-2.4-2.4-3.4");
        put(new String[]{"warlock", "колдун"}, "M3 12c2.6-4 5.6-6 9-6s6.4 2 9 6c-2.6 4-5.6 6-9 6s-6.4-2-9-6zM12 9.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2");
        put(new String[]{"wizard", "волшебник"}, "M4 7h5.6c1.3 0 2.4 1 2.4 2.2V20c0-1.2-1.1-2.2-2.4-2.2H4zM20 7h-5.6c-1.3 0-2.4 1-2.4 2.2V20c0-1.2 1.1-2.2 2.4-2.2H20zM12 2.2v2.2M9.2 2.8l.8 1.6M14.8 2.8l-.8 1.6");
        put(new String[]{"artificer", "изобретатель"}, "M12 4.4a7.6 7.6 0 1 0 0 15.2a7.6 7.6 0 1 0 0-15.2M12 8.6a3.4 3.4 0 1 0 0 6.8a3.4 3.4 0 1 0 0-6.8M12 1.8v2.6M12 19.6v2.6M1.8 12h2.6M19.6 12h2.6M4.4 4.4l1.8 1.8M17.8 17.8l1.8 1.8M19.6 4.4l-1.8 1.8M4.4 19.6l1.8-1.8");
    }

    private ClassIcons() {
    }

    /** Задан ли класс из справочника: «Неизвестно» и «Без класса» — нет. */
    public static boolean isKnown(@Nullable String klass) {
        return klass != null && ICONS.containsKey(normalize(klass));
    }

    public static void apply(@NonNull ImageView view, @Nullable String klass, @ColorInt int color) {
        String data = ICONS.get(normalize(klass));
        if (data == null) {
            return;
        }
        view.setColorFilter(null);
        view.setImageDrawable(new GlyphDrawable(data, color));
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static void put(@NonNull String[] aliases, @NonNull String path) {
        for (String alias : aliases) {
            ICONS.put(normalize(alias), path);
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
            paint.setStrokeWidth(1.7f);
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
