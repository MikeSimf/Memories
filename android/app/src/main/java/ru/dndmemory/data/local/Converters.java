package ru.dndmemory.data.local;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.dndmemory.data.model.EntityLink;

/** JSON-сериализация составных полей через Gson. */
public class Converters {

    private static final Gson GSON = new Gson();

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();

    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {
    }.getType();

    private static final Type LINK_LIST = new TypeToken<List<EntityLink>>() {
    }.getType();

    // --- tags / myPermissions -------------------------------------------------

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

    // --- attrs ---------------------------------------------------------------

    @TypeConverter
    public static String fromStringMap(Map<String, String> value) {
        return value == null ? null : GSON.toJson(value, STRING_MAP);
    }

    @TypeConverter
    public static Map<String, String> toStringMap(String value) {
        if (value == null || value.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> parsed = GSON.fromJson(value, STRING_MAP);
        return parsed == null ? new HashMap<>() : parsed;
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
}
