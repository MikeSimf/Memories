package ru.dndmemory.data.local;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.HeroItem;

/** JSON-сериализация составных полей через Gson. */
public class Converters {

    private static final Gson GSON = new Gson();

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();

    private static final Type LINK_LIST = new TypeToken<List<EntityLink>>() {
    }.getType();

    private static final Type HERO_ITEM_LIST = new TypeToken<List<HeroItem>>() {
    }.getType();

    // --- строковые списки (myPermissions) ------------------------------------

    @TypeConverter
    public static String fromStringList(List<String> value) {
        return value == null ? null : GSON.toJson(value, STRING_LIST);
    }

    @TypeConverter
    public static List<String> toStringList(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> parsed = GSON.fromJson(value, STRING_LIST);
        return parsed == null ? new ArrayList<>() : parsed;
    }

    // --- links ---------------------------------------------------------------

    @TypeConverter
    public static String fromLinkList(List<EntityLink> value) {
        return value == null ? null : GSON.toJson(value, LINK_LIST);
    }

    @TypeConverter
    public static List<EntityLink> toLinkList(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        List<EntityLink> parsed = GSON.fromJson(value, LINK_LIST);
        return parsed == null ? new ArrayList<>() : parsed;
    }

    // --- котомка героя (hero_items) ------------------------------------------

    @TypeConverter
    public static String fromHeroItemList(List<HeroItem> value) {
        return value == null ? null : GSON.toJson(value, HERO_ITEM_LIST);
    }

    @TypeConverter
    public static List<HeroItem> toHeroItemList(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        List<HeroItem> parsed = GSON.fromJson(value, HERO_ITEM_LIST);
        return parsed == null ? new ArrayList<>() : parsed;
    }
}
