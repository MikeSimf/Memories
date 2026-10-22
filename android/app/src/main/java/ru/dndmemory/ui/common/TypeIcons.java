package ru.dndmemory.ui.common;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
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

import ru.dndmemory.R;

/**
 * Значки рас и типов локаций/предметов.
 *
 * <p>Расы — заливные символичные значки (уши эльфа, борода дварфа, клюв кенку),
 * как у классов. Пол на значок не влияет: он показывается отдельной меткой.
 * Локации и предметы остались контурными глифами.</p>
 */
public final class TypeIcons {

    private static final Map<String, Integer> RACES = new HashMap<>();
    private static final Map<String, String> LOCATIONS = new HashMap<>();
    private static final Map<String, String> ITEMS = new HashMap<>();

    static {
        put(RACES, new String[]{"человек"}, R.drawable.race_human);
        put(RACES, new String[]{"эльф"}, R.drawable.race_elf);
        put(RACES, new String[]{"полуэльф"}, R.drawable.race_half_elf);
        put(RACES, new String[]{"дварф"}, R.drawable.race_dwarf);
        put(RACES, new String[]{"дуэргар"}, R.drawable.race_duergar);
        put(RACES, new String[]{"гном"}, R.drawable.race_gnome);
        put(RACES, new String[]{"полурослик"}, R.drawable.race_halfling);
        put(RACES, new String[]{"халфлинг"}, R.drawable.race_halfling);
        put(RACES, new String[]{"орк"}, R.drawable.race_orc);
        put(RACES, new String[]{"полуорк"}, R.drawable.race_half_orc);
        put(RACES, new String[]{"тифлинг"}, R.drawable.race_tiefling);
        put(RACES, new String[]{"аасимар"}, R.drawable.race_aasimar);
        put(RACES, new String[]{"дженази"}, R.drawable.race_genasi_air);
        put(RACES, new String[]{"дженази воздуха"}, R.drawable.race_genasi_air);
        put(RACES, new String[]{"дженази земли"}, R.drawable.race_genasi_earth);
        put(RACES, new String[]{"дженази огня"}, R.drawable.race_genasi_fire);
        put(RACES, new String[]{"дженази воды"}, R.drawable.race_genasi_water);
        put(RACES, new String[]{"голиаф"}, R.drawable.race_goliath);
        put(RACES, new String[]{"фирболг"}, R.drawable.race_firbolg);
        put(RACES, new String[]{"кенку"}, R.drawable.race_kenku);
        put(RACES, new String[]{"табакси"}, R.drawable.race_tabaxi);
        put(RACES, new String[]{"леонин"}, R.drawable.race_leonin);
        put(RACES, new String[]{"локсодон"}, R.drawable.race_loxodon);
        put(RACES, new String[]{"грибнит"}, R.drawable.race_grung);
        put(RACES, new String[]{"тортл"}, R.drawable.race_tortle);
        put(RACES, new String[]{"три-крин"}, R.drawable.race_thri_kreen);
        put(RACES, new String[]{"плазмоид"}, R.drawable.race_plasmoid);
        put(RACES, new String[]{"харенгон"}, R.drawable.race_harengon);
        put(RACES, new String[]{"оулин"}, R.drawable.race_owlin);
        put(RACES, new String[]{"сатир"}, R.drawable.race_satyr);
        put(RACES, new String[]{"шадар-кай"}, R.drawable.race_shadar_kai);
        put(RACES, new String[]{"подменыш"}, R.drawable.race_changeling);
        put(RACES, new String[]{"гитъянки"}, R.drawable.race_githyanki);
        put(RACES, new String[]{"гитзераи"}, R.drawable.race_githzerai);
        put(RACES, new String[]{"багбир"}, R.drawable.race_bugbear);
        put(RACES, new String[]{"кобольд"}, R.drawable.race_kobold);
        put(RACES, new String[]{"людоящер"}, R.drawable.race_lizardfolk);
        put(RACES, new String[]{"виашино"}, R.drawable.race_viashino);
        put(RACES, new String[]{"минотавр"}, R.drawable.race_minotaur);
        put(RACES, new String[]{"кентавр"}, R.drawable.race_centaur);
        put(RACES, new String[]{"нага"}, R.drawable.race_naga);
        put(RACES, new String[]{"гифф"}, R.drawable.race_giff);
        put(RACES, new String[]{"ааракокра"}, R.drawable.race_aarakocra);
        put(RACES, new String[]{"авен"}, R.drawable.race_aven);
        put(RACES, new String[]{"эладрин"}, R.drawable.race_eladrin);
        put(RACES, new String[]{"тритон"}, R.drawable.race_triton);
        put(RACES, new String[]{"юань-ти"}, R.drawable.race_yuan_ti);
        put(RACES, new String[]{"калаштар"}, R.drawable.race_kalashtar);
        put(RACES, new String[]{"шифтер"}, R.drawable.race_shifter);
        put(RACES, new String[]{"warforged"}, R.drawable.race_warforged);
        put(RACES, new String[]{"ведалкен"}, R.drawable.race_vedalken);
        put(RACES, new String[]{"вердан"}, R.drawable.race_verdan);
        put(RACES, new String[]{"хадози"}, R.drawable.race_hadozee);
        put(RACES, new String[]{"дампир"}, R.drawable.race_dhampir);
        put(RACES, new String[]{"хексблад"}, R.drawable.race_hexblood);
        put(RACES, new String[]{"реборн"}, R.drawable.race_reborn);
        put(RACES, new String[]{"автогном"}, R.drawable.race_autognome);
        put(RACES, new String[]{"кенра"}, R.drawable.race_kenra);
        put(RACES, new String[]{"мерфолк"}, R.drawable.race_merfolk);
        put(RACES, new String[]{"локата"}, R.drawable.race_locathah);
        put(RACES, new String[]{"сирена"}, R.drawable.race_siren);
        put(RACES, new String[]{"вампир"}, R.drawable.race_vampire);
        put(RACES, new String[]{"глючный"}, R.drawable.race_glitchling);
        put(RACES, new String[]{"хобгоблин"}, R.drawable.race_hobgoblin);
        put(RACES, new String[]{"гоблин"}, R.drawable.race_goblin);
        put(RACES, new String[]{"гризли"}, R.drawable.race_grizzli);
        put(RACES, new String[]{"пустотный"}, R.drawable.race_voidmind);
        put(RACES, new String[]{"теневой фей"}, R.drawable.race_shadow_fey);
        put(RACES, new String[]{"гнолл"}, R.drawable.race_gnoll);
        put(RACES, new String[]{"кэндэр"}, R.drawable.race_kender);
        put(RACES, new String[]{"леша"}, R.drawable.race_leshy);
        put(RACES, new String[]{"орлах"}, R.drawable.race_orlach);
        put(RACES, new String[]{"ревенант"}, R.drawable.race_revenant);
        put(RACES, new String[]{"симик-гибрид"}, R.drawable.race_simic_hybrid);
        put(RACES, new String[]{"драконорождённый"}, R.drawable.race_dragonborn);
        put(RACES, new String[]{"драконорожденный"}, R.drawable.race_dragonborn);
        put(RACES, new String[]{"дроу"}, R.drawable.race_elf);
        put(RACES, new String[]{"неизвестно"}, R.drawable.race_unknown);
        put(RACES, new String[]{"без расы"}, R.drawable.race_unknown);
        put(LOCATIONS, new String[]{"unknown", "неизвестно"},
                "M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2M9.4 9.2a2.6 2.6 0 1 1 4.2 2.2c-.9.6-1.6 1.2-1.6 2.4M12 17.2h.01");
        put(LOCATIONS, new String[]{"city", "город"},
                "M3.4 20V9.6h4V6h3.2v3.6h3.2V4.4h3.2V9.6h4V20zM6.4 12.4h1.2M6.4 15.6h1.2M16.4 12.4h1.2M16.4 15.6h1.2M11.6 13.2h1.2v2.4h-1.2z");
        put(LOCATIONS, new String[]{"village", "деревня"},
                "M3.4 20v-6.6L12 6l8.6 7.4V20zM9.4 20v-4.6h5.2V20");
        put(LOCATIONS, new String[]{"forest", "лес"},
                "M12 2.8l4 6.4h-2.4l3.2 5H7.2l3.2-5H8zM12 14.2V21");
        put(LOCATIONS, new String[]{"plain", "равнина"},
                "M2.6 18.4h18.8M4 18.4c3-2.4 5.6-2.4 8 0M12 18.4c3-2.4 5.6-2.4 8 0M12 3.4a3.4 3.4 0 1 0 0 6.8a3.4 3.4 0 1 0 0-6.8M12 2v2M12 9.6v2M8.6 5.8 7 4.6M15.4 5.8 17 4.6");
        put(LOCATIONS, new String[]{"steppe", "степь"},
                "M2.6 19h18.8M5 19c0-2.6 1-4.8 2.4-6.4M9 19c0-2 1-4 2.4-5.6M13.4 19c0-2.8 1.2-5.2 2.8-6.8M18 19c0-1.8.8-3.4 2-4.6");
        put(LOCATIONS, new String[]{"crossroads", "перекрёсток", "перекресток"},
                "M12 2.6v18.8M2.6 12h18.8M12 2.6 9.4 5.4M12 2.6l2.6 2.8M21.4 12l-2.8-2.6M21.4 12l-2.8 2.6M12 21.4l-2.6-2.8M12 21.4l2.6-2.8M2.6 12l2.8-2.6M2.6 12l2.8 2.6");
        put(LOCATIONS, new String[]{"otherworld", "другое измерение"},
                "M12 2.8a9.2 9.2 0 1 0 0 18.4a9.2 9.2 0 1 0 0-18.4M12 6.2a5.8 5.8 0 1 0 0 11.6a5.8 5.8 0 1 0 0-11.6M12 9.8a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4");
        put(LOCATIONS, new String[]{"mountains", "горы"},
                "M2.4 19.6 8.6 7.2l3.2 6 2.2-3.6 7.6 10zM8.6 7.2l1.6 3.2M5.6 13.2h2.6");
        put(LOCATIONS, new String[]{"cave", "пещера"},
                "M3 20V12C3 7 7 3.4 12 3.4S21 7 21 12v8zM8.2 20v-4.6a3.8 3.8 0 0 1 7.6 0V20");
        put(LOCATIONS, new String[]{"dungeon", "подземелье"},
                "M4 20V4h16v16zM4 8h16M4 12h16M4 16h16M8 4v16M12 4v16M16 4v16");
        put(LOCATIONS, new String[]{"ruins", "руины"},
                "M3.4 20V8.6h3V20M9.4 20V5.4h3.2V20M15.8 20v-7.4h3V20M2.6 20h18.8M3.4 8.6l3-2.4M9.4 5.4l3.2-2");
        put(LOCATIONS, new String[]{"tower", "башня"},
                "M8 20V8l4-5 4 5v12zM6 20h12M8 8h8M8 12h8M8 16h8");
        put(LOCATIONS, new String[]{"swamp", "болото"},
                "M2.6 19.4h18.8M6 19.4v-6M6 13.4c-1.8 0-3-1.4-3-3M6 15.4c1.8 0 3-1.4 3-3M11 19.4v-8M11 11.4c-1.8 0-3-1.4-3-3M11 14c1.8 0 3-1.4 3-3M16.4 19.4v-5M16.4 14.4c-1.6 0-2.6-1.2-2.6-2.6");
        put(LOCATIONS, new String[]{"coast", "побережье"},
                "M2.6 16.6c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8M12 3.4v10M8.4 7l3.6-3.6L15.6 7");
        put(LOCATIONS, new String[]{"sea", "море"},
                "M2.6 9.4c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8M2.6 14.2c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8M2.6 19c2.4 0 2.4 1.8 4.8 1.8s2.4-1.8 4.8-1.8 2.4 1.8 4.8 1.8 2.4-1.8 4.4-1.8");
        put(LOCATIONS, new String[]{"desert", "пустыня"},
                "M2.6 19h18.8M4 19c0-3.4 2.4-6.2 5.6-6.2 2.2 0 3.6 1 4.4 2.4M9 19c0-2.6 1.8-4.8 4.2-4.8 2 0 3.4 1 4 2.4M15.4 19c0-2 1.4-3.6 3.2-3.6M17 4.4a3 3 0 1 0 0 6a3 3 0 1 0 0-6M17 2v1.4M17 11.4V13M13.4 7.4h1.4M19.2 7.4h1.4M14.6 5l-1-1M19.4 5l1-1");
        put(ITEMS, new String[]{"unknown", "неизвестно"},
                "M12 3.4a8.6 8.6 0 1 0 0 17.2a8.6 8.6 0 1 0 0-17.2M9.4 9.2a2.6 2.6 0 1 1 4.2 2.2c-.9.6-1.6 1.2-1.6 2.4M12 17.2h.01");
        put(ITEMS, new String[]{"weapon", "оружие"},
                "M12 2.6v13M8.6 5.4 12 2.4l3.4 3M8.8 15.6h6.4M10.4 15.6l-2.2 5.8M13.6 15.6l2.2 5.8M9 12.4h6");
        put(ITEMS, new String[]{"armor", "броня"},
                "M12 3 20 5.8v6c0 4.4-3.2 7.4-8 9.2-4.8-1.8-8-4.8-8-9.2v-6zM12 7.4v9.2M8.6 10.6h6.8");
        put(ITEMS, new String[]{"artifact", "артефакт"},
                "M12 2.8 20.4 8.6 17.2 19.4H6.8L3.6 8.6zM3.6 8.6h16.8M12 2.8v16.6M8.2 8.6 12 19.4M15.8 8.6 12 19.4");
        put(ITEMS, new String[]{"jewelry", "драгоценность"},
                "M12 8.6a7 7 0 1 0 0 14a7 7 0 1 0 0-14M12 5.4l-2 3.2h4zM12 3.2l2 2.2M12 3.2 10 5.4");
        put(ITEMS, new String[]{"potion", "зелье"},
                "M9.4 3h5.2v3l3 4.4c1 1.4 1.6 3 1.6 4.8 0 3.4-2.6 6-6 6s-6-2.6-6-6c0-1.8.6-3.4 1.6-4.8l3-4.4zM8.2 14.4c1.6 1 2.8 1 4 0s2.4-1 3.6 0M9.4 3h5.2");
        put(ITEMS, new String[]{"scroll", "свиток"},
                "M6 4h11.4c1.2 0 2.2 1 2.2 2.2v11.6c0 1.2-1 2.2-2.2 2.2H6M6 4c-1.2 0-2.2 1-2.2 2.2S4.8 8.4 6 8.4h2.2v11.4c0 1.2-1 2.2-2.2 2.2s-2.2-1-2.2-2.2M8.2 8.4h9M8.2 12h9M8.2 15.6h5.4");
        put(ITEMS, new String[]{"trophy", "трофей"},
                "M7.4 3h9.2v6.4a4.6 4.6 0 0 1-9.2 0zM7.4 5H4.6v2.2c0 1.8 1.2 3.2 3 3.2M16.6 5h2.8v2.2c0 1.8-1.2 3.2-3 3.2M12 14v4M8.4 21h7.2M9.6 21c0-1.8 1-3 2.4-3s2.4 1.2 2.4 3");
        put(ITEMS, new String[]{"tool", "инструмент"},
                "M14.6 2.8 21.2 9.4l-3 3-6.6-6.6zM11 8.4 3 16.4 4.6 18l7-7M5.6 3.4 9.4 7.2M3 4.8l3.6 3.6");
        put(ITEMS, new String[]{"book", "книга"},
                "M4 4.4h6.2c1 0 1.8.8 1.8 1.8v13.4c0-1-.8-1.8-1.8-1.8H4zM20 4.4h-6.2c-1 0-1.8.8-1.8 1.8v13.4c0-1 .8-1.8 1.8-1.8H20z");
        put(ITEMS, new String[]{"transport", "транспорт"},
                "M3 6h11v9H3zM14 9h4l3 4v2h-7zM7 18.6a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6M17 18.6a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2");
        put(ITEMS, new String[]{"treasure", "сокровище"},
                "M3 10h18v9.4H3zM3 10C3 6.6 6 4 12 4s9 2.6 9 6M10 4v3M3 13.4h18M10.6 13.4h2.8v2.8h-2.8z");
        put(ITEMS, new String[]{"other", "другое"},
                "M12 2.8 20.4 7.4v9.2L12 21.2 3.6 16.6V7.4zM3.6 7.4 12 12l8.4-4.6M12 12v9.2");
    }

    private TypeIcons() {
    }

    @Nullable
    public static Integer raceRes(@Nullable String race) {
        return RACES.get(normalize(race));
    }

    /** Глиф по классу объекта: у локаций и предметов — контурный путь. */
    @Nullable
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
            return null;
        }
        String path = map.get(normalize(value));
        return path == null ? map.get("неизвестно") : path;
    }

    public static void applyRace(@NonNull ImageView view, @Nullable String race,
                                 @ColorInt int color) {
        Integer res = raceRes(race);
        if (res == null) {
            res = R.drawable.race_unknown;
        }
        view.setImageResource(res);
        view.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    public static void apply(@NonNull ImageView view, @Nullable String kind,
                             @Nullable String value, @ColorInt int color) {
        if ("LOCATION".equalsIgnoreCase(kind) || "ITEM".equalsIgnoreCase(kind)
                || "MATERIAL".equalsIgnoreCase(kind)) {
            view.setColorFilter(null);
            view.setImageDrawable(new GlyphDrawable(pathFor(kind, value), color));
            return;
        }
        applyRace(view, value, color);
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static void put(@NonNull Map<String, Integer> map, @NonNull String[] aliases, int res) {
        for (String alias : aliases) {
            map.put(normalize(alias), res);
        }
    }

    private static void put(@NonNull Map<String, String> map, @NonNull String[] aliases,
                            @NonNull String path) {
        for (String alias : aliases) {
            map.put(normalize(alias), path);
        }
    }

    private static final class GlyphDrawable extends Drawable {
        private final Path path;
        private final Paint paint;

        GlyphDrawable(@Nullable String data, @ColorInt int color) {
            path = data == null ? null : PathParser.createPathFromPathData(data);
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
