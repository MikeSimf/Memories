package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.web.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Глобальные справочники для кабинета system. */
@Service
@RequiredArgsConstructor
public class GlobalDictionaryService {

    private final JdbcTemplate jdbc;

    public static final String RACE = "RACE";
    public static final String CLASS = "CLASS";
    public static final String ITEM_TYPE = "ITEM_TYPE";
    public static final String LOCATION_TYPE = "LOCATION_TYPE";

    @Transactional(readOnly = true)
    public static class Item {
        public String name;
        public String iconFileId;

        Item(String name, String iconFileId) {
            this.name = name;
            this.iconFileId = iconFileId;
        }
    }

    public Map<String, List<Item>> all() {
        Map<String, List<Item>> result = new LinkedHashMap<>();
        result.put(RACE, items("select name, icon_file_id from races order by name"));
        result.put(CLASS, items("select name, icon_file_id from character_classes order by sort_order, name"));
        result.put(ITEM_TYPE, items("select name, icon_file_id from item_types order by sort_order, name"));
        result.put(LOCATION_TYPE, items("select name, icon_file_id from location_types order by sort_order, name"));
        return result;
    }

    @Transactional
    public void setIcon(String kind, String name, String iconFileId) {
        String table = table(kind);
        jdbc.update("update " + table + " set icon_file_id = ? where name = ?",
                iconFileId == null || iconFileId.isBlank() ? null : java.util.UUID.fromString(iconFileId), name);
    }

    private List<Item> items(String sql) {
        List<Item> result = new ArrayList<>();
        jdbc.query(sql, rs -> {
            String icon = rs.getString("icon_file_id");
            result.add(new Item(rs.getString("name"), icon));
        });
        return result;
    }

    @Transactional
    public void add(String kind, String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) throw ApiException.badRequest("Укажите название");
        String code = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9а-я]+", "_");
        if (RACE.equals(kind)) {
            jdbc.update("insert into races (code, name, sort_order) values (?, ?, 500) on conflict (code) do nothing",
                    code, value);
        } else if (CLASS.equals(kind)) {
            jdbc.update("insert into character_classes (code, name, sort_order) values (?, ?, 500) on conflict (code) do nothing",
                    code, value);
        } else if (ITEM_TYPE.equals(kind)) {
            jdbc.update("insert into item_types (code, name, sort_order) values (?, ?, 500) on conflict (code) do nothing",
                    code, value);
        } else if (LOCATION_TYPE.equals(kind)) {
            jdbc.update("insert into location_types (code, name, sort_order) values (?, ?, 500) on conflict (code) do nothing",
                    code, value);
        } else {
            throw ApiException.badRequest("Неизвестный справочник");
        }
    }

    @Transactional
    public void remove(String kind, String name) {
        if (used(kind, name)) {
            throw ApiException.validation("Значение используется — удалить нельзя");
        }
        String table = table(kind);
        String column = column(kind);
        jdbc.update("delete from " + table + " where " + column + " = ?", name);
    }

    private boolean used(String kind, String name) {
        if (RACE.equals(kind)) {
            Integer count = jdbc.queryForObject(
                    "select (select count(*) from heroes where race = ?) + (select count(*) from characters where race = ?)",
                    Integer.class, name, name);
            return count != null && count > 0;
        }
        if (CLASS.equals(kind)) {
            Integer count = jdbc.queryForObject(
                    "select (select count(*) from heroes where klass = ?) + (select count(*) from characters where klass = ?)",
                    Integer.class, name, name);
            return count != null && count > 0;
        }
        if (ITEM_TYPE.equals(kind)) {
            Integer count = jdbc.queryForObject("select count(*) from items where item_type = ?", Integer.class, name);
            return count != null && count > 0;
        }
        Integer count = jdbc.queryForObject("select count(*) from locations where location_type = ?", Integer.class, name);
        return count != null && count > 0;
    }

    private String table(String kind) {
        if (RACE.equals(kind)) return "races";
        if (CLASS.equals(kind)) return "character_classes";
        if (ITEM_TYPE.equals(kind)) return "item_types";
        if (LOCATION_TYPE.equals(kind)) return "location_types";
        throw ApiException.badRequest("Неизвестный справочник");
    }

    private String column(String kind) {
        if (RACE.equals(kind)) return "name";
        if (CLASS.equals(kind)) return "name";
        if (ITEM_TYPE.equals(kind)) return "name";
        if (LOCATION_TYPE.equals(kind)) return "name";
        throw ApiException.badRequest("Неизвестный справочник");
    }

    private List<String> names(String sql) {
        return new ArrayList<>(jdbc.queryForList(sql, String.class));
    }
}
