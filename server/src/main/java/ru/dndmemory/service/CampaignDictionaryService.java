package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Member;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Какие расы, типы предметов и локаций разрешены в конкретной кампании. */
@Service
@RequiredArgsConstructor
public class CampaignDictionaryService {

    public static final String RACE = "RACE";
    public static final String ITEM_TYPE = "ITEM_TYPE";
    public static final String LOCATION_TYPE = "LOCATION_TYPE";

    private final JdbcTemplate jdbc;
    private final PermissionService permissionService;

    public static class Entry {
        private final String kind;
        private final String name;
        private final boolean enabled;
        private final String iconFileId;
        private final boolean used;

        public Entry(String kind, String name, boolean enabled, String iconFileId, boolean used) {
            this.iconFileId = iconFileId;
            this.kind = kind;
            this.name = name;
            this.enabled = enabled;
            this.used = used;
        }

        public String getKind() {
            return kind;
        }

        public String getName() {
            return name;
        }

        public String getIconFileId() {
            return iconFileId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isUsed() {
            return used;
        }
    }

    @Transactional(readOnly = true)
    public List<Entry> list(UUID campaignId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        List<Entry> result = new ArrayList<>();
        for (String kind : Arrays.asList(RACE, ITEM_TYPE, LOCATION_TYPE)) {
            Set<String> disabled = new LinkedHashSet<>(jdbc.queryForList(
                    "select name from campaign_dictionary_disabled where campaign_id = ? and kind = ?",
                    String.class, campaignId, kind));
            java.util.Map<String, String> icons = icons(kind);
            for (String name : names(kind)) {
                result.add(new Entry(kind, name, !disabled.contains(name), icons.get(name),
                        used(campaignId, kind, name) > 0));
            }
        }
        return result;
    }

    @Transactional
    public void setEnabled(UUID campaignId, UUID userId, String kind, String name, boolean enabled) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.CAMPAIGN_MANAGE);
        if (!Arrays.asList(RACE, ITEM_TYPE, LOCATION_TYPE).contains(kind)) {
            throw ApiException.badRequest("Неизвестный справочник");
        }
        if (!enabled && used(campaignId, kind, name) > 0) {
            throw ApiException.validation("Значение уже используется — снять нельзя");
        }
        if (enabled) {
            jdbc.update("delete from campaign_dictionary_disabled where campaign_id = ? and kind = ? and name = ?",
                    campaignId, kind, name);
        } else {
            jdbc.update("insert into campaign_dictionary_disabled (campaign_id, kind, name) values (?, ?, ?) "
                    + "on conflict do nothing", campaignId, kind, name);
        }
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID campaignId, String kind, String name) {
        Integer count = jdbc.queryForObject(
                "select count(*) from campaign_dictionary_disabled where campaign_id = ? and kind = ? and name = ?",
                Integer.class, campaignId, kind, name);
        return count == null || count == 0;
    }

    private java.util.Map<String, String> icons(String kind) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        String table = RACE.equals(kind) ? "races" : ITEM_TYPE.equals(kind) ? "item_types" : "location_types";
        jdbc.query("select name, icon_file_id from " + table, rs -> {
            result.put(rs.getString("name"), rs.getString("icon_file_id"));
        });
        return result;
    }

    private List<String> names(String kind) {
        if (RACE.equals(kind)) {
            return jdbc.queryForList("select name from races order by name", String.class);
        }
        if (ITEM_TYPE.equals(kind)) {
            return jdbc.queryForList("select name from item_types order by sort_order, name", String.class);
        }
        if (LOCATION_TYPE.equals(kind)) {
            return jdbc.queryForList("select name from location_types order by sort_order, name", String.class);
        }
        return new ArrayList<>();
    }

    private int used(UUID campaignId, String kind, String name) {
        Integer count;
        if (RACE.equals(kind)) {
            count = jdbc.queryForObject(
                    "select (select count(*) from heroes where campaign_id = ? and race = ? and deleted = false)"
                            + " + (select count(*) from characters where campaign_id = ? and race = ? and deleted = false)",
                    Integer.class, campaignId, name, campaignId, name);
        } else if (ITEM_TYPE.equals(kind)) {
            count = jdbc.queryForObject(
                    "select count(*) from items where campaign_id = ? and item_type = ? and deleted = false",
                    Integer.class, campaignId, name);
        } else if (LOCATION_TYPE.equals(kind)) {
            count = jdbc.queryForObject(
                    "select count(*) from locations where campaign_id = ? and location_type = ? and deleted = false",
                    Integer.class, campaignId, name);
        } else {
            count = 0;
        }
        return count == null ? 0 : count;
    }
}
