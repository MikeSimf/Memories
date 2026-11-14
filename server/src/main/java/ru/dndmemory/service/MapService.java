package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Интерактивные карты кампании и метки объектов на них. */
@Service
@RequiredArgsConstructor
public class MapService {

    private final JdbcTemplate jdbc;
    private final PermissionService permissions;

    public static class MapDto {
        public String id;
        public String name;
        public String description;
        public String imageFileId;
        public int markerCount;
        public String updatedAt;
    }

    public static class MarkerDto {
        public String id;
        public String entityId;
        public String name;
        public String objectType;
        public String avatarFileId;
        public double x;
        public double y;
    }

    public static class MapDetailDto extends MapDto {
        public List<MarkerDto> markers = new ArrayList<>();
    }

    public static class MapRequest {
        public String name;
        public String description;
        public String imageFileId;
    }

    public static class MarkerRequest {
        public String entityId;
        public double x;
        public double y;
    }

    @Transactional(readOnly = true)
    public List<MapDto> list(UUID campaignId, UUID userId) {
        permissions.requireMember(campaignId, userId);
        List<MapDto> result = new ArrayList<>();
        jdbc.query("select m.*, (select count(*) from map_markers k where k.map_id = m.id) as marker_count "
                        + "from interactive_maps m where m.campaign_id = ? and m.deleted = false order by m.created_at",
                rs -> {
                    MapDto dto = new MapDto();
                    dto.id = rs.getString("id");
                    dto.name = rs.getString("name");
                    dto.description = rs.getString("description");
                    dto.imageFileId = rs.getString("image_file_id");
                    dto.markerCount = rs.getInt("marker_count");
                    dto.updatedAt = rs.getString("updated_at");
                    result.add(dto);
                }, campaignId);
        return result;
    }

    @Transactional(readOnly = true)
    public MapDetailDto get(UUID campaignId, UUID mapId, UUID userId) {
        permissions.requireMember(campaignId, userId);
        MapDetailDto dto = jdbc.query("select * from interactive_maps where id = ? and campaign_id = ? and deleted = false",
                rs -> {
                    if (!rs.next()) return null;
                    MapDetailDto x = new MapDetailDto();
                    x.id = rs.getString("id");
                    x.name = rs.getString("name");
                    x.description = rs.getString("description");
                    x.imageFileId = rs.getString("image_file_id");
                    x.updatedAt = rs.getString("updated_at");
                    return x;
                }, mapId, campaignId);
        if (dto == null) throw ApiException.notFound("Карта не найдена");
        jdbc.query("select k.*, coalesce(h.name, c.name, l.name, i.name) as entity_name, "
                        + "coalesce(h.avatar_file_id, c.avatar_file_id, NULL) as avatar, "
                        + "case when h.id is not null then 'HERO' when c.id is not null then 'CHARACTER' "
                        + "when l.id is not null then 'LOCATION' else 'ITEM' end as kind "
                        + "from map_markers k "
                        + "left join heroes h on h.id = k.entity_id and h.deleted = false "
                        + "left join characters c on c.id = k.entity_id and c.deleted = false "
                        + "left join locations l on l.id = k.entity_id and l.deleted = false "
                        + "left join items i on i.id = k.entity_id and i.deleted = false "
                        + "where k.map_id = ? and (h.id is not null or c.id is not null or l.id is not null or i.id is not null)",
                rs -> {
                    MarkerDto marker = new MarkerDto();
                    marker.id = rs.getString("id");
                    marker.entityId = rs.getString("entity_id");
                    marker.name = rs.getString("entity_name");
                    marker.objectType = rs.getString("kind");
                    marker.avatarFileId = rs.getString("avatar");
                    marker.x = rs.getDouble("x");
                    marker.y = rs.getDouble("y");
                    dto.markers.add(marker);
                }, mapId);
        dto.markerCount = dto.markers.size();
        return dto;
    }

    @Transactional
    public MapDetailDto create(UUID campaignId, UUID userId, MapRequest request) {
        permissions.require(permissions.requireMember(campaignId, userId),
                ru.dndmemory.security.Permissions.ENTITY_WRITE);
        if (request.name == null || request.name.trim().isEmpty()) {
            throw ApiException.validation("Укажите название карты");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into interactive_maps (id, campaign_id, name, description, image_file_id, created_by, updated_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, campaignId, request.name.trim(), request.description,
                uuidOrNull(request.imageFileId), userId, userId);
        return get(campaignId, id, userId);
    }

    @Transactional
    public MapDetailDto update(UUID campaignId, UUID mapId, UUID userId, MapRequest request) {
        permissions.require(permissions.requireMember(campaignId, userId),
                ru.dndmemory.security.Permissions.ENTITY_WRITE);
        if (request.name == null || request.name.trim().isEmpty()) {
            throw ApiException.validation("Укажите название карты");
        }
        int rows = jdbc.update("update interactive_maps set name = ?, description = ?, image_file_id = ?, "
                        + "updated_by = ?, revision = revision + 1, updated_at = now() "
                        + "where id = ? and campaign_id = ? and deleted = false",
                request.name.trim(), request.description, uuidOrNull(request.imageFileId), userId, mapId, campaignId);
        if (rows == 0) throw ApiException.notFound("Карта не найдена");
        return get(campaignId, mapId, userId);
    }

    @Transactional
    public void delete(UUID campaignId, UUID mapId, UUID userId) {
        permissions.require(permissions.requireMember(campaignId, userId),
                ru.dndmemory.security.Permissions.ENTITY_WRITE);
        jdbc.update("update interactive_maps set deleted = true, updated_at = now() where id = ? and campaign_id = ?",
                mapId, campaignId);
    }

    @Transactional
    public void addMarker(UUID campaignId, UUID mapId, UUID userId, MarkerRequest request) {
        permissions.require(permissions.requireMember(campaignId, userId),
                ru.dndmemory.security.Permissions.ENTITY_WRITE);
        UUID entityId = uuidOrNull(request.entityId);
        if (entityId == null) throw ApiException.validation("Выберите объект");
        Integer exists = jdbc.queryForObject("select count(*) from ("
                        + "select id from heroes where id = ? and campaign_id = ? and deleted = false union all "
                        + "select id from characters where id = ? and campaign_id = ? and deleted = false union all "
                        + "select id from locations where id = ? and campaign_id = ? and deleted = false union all "
                        + "select id from items where id = ? and campaign_id = ? and deleted = false) x",
                Integer.class, entityId, campaignId, entityId, campaignId, entityId, campaignId, entityId, campaignId);
        if (exists == null || exists == 0) throw ApiException.validation("Объект не найден в кампании");
        jdbc.update("insert into map_markers (id, map_id, entity_id, x, y, created_by) values (?, ?, ?, ?, ?, ?) "
                        + "on conflict (map_id, entity_id) do update set x = excluded.x, y = excluded.y",
                UUID.randomUUID(), mapId, entityId, clamp(request.x), clamp(request.y), userId);
    }

    @Transactional
    public void deleteMarker(UUID campaignId, UUID mapId, UUID markerId, UUID userId) {
        permissions.require(permissions.requireMember(campaignId, userId),
                ru.dndmemory.security.Permissions.ENTITY_WRITE);
        jdbc.update("delete from map_markers where id = ? and map_id = ?", markerId, mapId);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.trim().isEmpty() || "null".equals(value)) return null;
        try { return UUID.fromString(value.trim()); } catch (RuntimeException e) { return null; }
    }
}
