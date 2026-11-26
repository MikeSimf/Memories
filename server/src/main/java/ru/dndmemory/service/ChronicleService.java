package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Хроники кампании: сессии, пересказы и точки перемещения по картам. */
@Service
@RequiredArgsConstructor
public class ChronicleService {

    private final JdbcTemplate jdbc;
    private final PermissionService permissions;

    public static class ChronicleDto {
        public String id;
        public String name;
        public String date;
        public List<RetellingRef> retellings = new ArrayList<>();
        public List<PointDto> points = new ArrayList<>();
    }

    public static class RetellingRef {
        public String id;
        public String title;
    }

    public static class PointDto {
        public String id;
        public String chronicleId;
        public String mapId;
        public String mapName;
        public String heroId;
        public String heroName;
        public String heroColor;
        public boolean wholeTeam;
        public double x;
        public double y;
        public int order;
        public boolean teleport;
    }

    public static class ChronicleRequest {
        public String name;
        public LocalDate date;
    }

    public static class PointRequest {
        public String mapId;
        public String heroId;
        public boolean wholeTeam = true;
        public double x;
        public double y;
        public int order;
        public boolean teleport;
    }

    @Transactional(readOnly = true)
    public List<ChronicleDto> list(UUID campaignId, UUID userId) {
        permissions.requireMember(campaignId, userId);
        List<ChronicleDto> result = new ArrayList<>();
        jdbc.query("select * from chronicles where campaign_id = ? and deleted = false order by session_date desc nulls last, lower(name)",
                rs -> {
                    ChronicleDto dto = new ChronicleDto();
                    dto.id = rs.getString("id");
                    dto.name = rs.getString("name");
                    dto.date = rs.getString("session_date");
                    result.add(dto);
                }, campaignId);
        for (ChronicleDto dto : result) loadChildren(dto);
        return result;
    }

    @Transactional(readOnly = true)
    public ChronicleDto get(UUID campaignId, UUID id, UUID userId) {
        permissions.requireMember(campaignId, userId);
        ChronicleDto dto = jdbc.query("select * from chronicles where id = ? and campaign_id = ? and deleted = false",
                rs -> {
                    if (!rs.next()) return null;
                    ChronicleDto x = new ChronicleDto();
                    x.id = rs.getString("id");
                    x.name = rs.getString("name");
                    x.date = rs.getString("session_date");
                    return x;
                }, id, campaignId);
        if (dto == null) throw ApiException.notFound("Хроника не найдена");
        loadChildren(dto);
        return dto;
    }

    private void loadChildren(ChronicleDto dto) {
        jdbc.query("select id, title from retellings where chronicle_id = ? and deleted = false order by session_date nulls last, lower(title)",
                rs -> {
                    RetellingRef ref = new RetellingRef();
                    ref.id = rs.getString("id");
                    ref.title = rs.getString("title");
                    dto.retellings.add(ref);
                }, dto.id);
        jdbc.query("select p.*, m.name as map_name, h.name as hero_name, h.color as hero_color "
                        + "from chronicle_points p join interactive_maps m on m.id = p.map_id "
                        + "left join heroes h on h.id = p.hero_id "
                        + "where p.chronicle_id = ? order by p.sort_order, p.created_at",
                rs -> {
                    PointDto point = new PointDto();
                    point.id = rs.getString("id");
                    point.chronicleId = rs.getString("chronicle_id");
                    point.mapId = rs.getString("map_id");
                    point.mapName = rs.getString("map_name");
                    point.heroId = rs.getString("hero_id");
                    point.heroName = rs.getString("hero_name");
                    point.heroColor = rs.getString("hero_color");
                    point.wholeTeam = rs.getBoolean("whole_team");
                    point.x = rs.getDouble("x");
                    point.y = rs.getDouble("y");
                    point.order = rs.getInt("sort_order");
                    point.teleport = rs.getBoolean("teleport");
                    dto.points.add(point);
                }, dto.id);
    }

    @Transactional
    public ChronicleDto create(UUID campaignId, UUID userId, ChronicleRequest request) {
        permissions.require(permissions.requireMember(campaignId, userId), Permissions.ENTITY_WRITE);
        if (request.name == null || request.name.trim().isEmpty()) throw ApiException.validation("Укажите название хроники");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into chronicles (id, campaign_id, name, session_date, created_by, updated_by) values (?, ?, ?, ?, ?, ?)",
                id, campaignId, request.name.trim(), request.date, userId, userId);
        return get(campaignId, id, userId);
    }

    @Transactional
    public ChronicleDto update(UUID campaignId, UUID id, UUID userId, ChronicleRequest request) {
        permissions.require(permissions.requireMember(campaignId, userId), Permissions.ENTITY_WRITE);
        if (request.name == null || request.name.trim().isEmpty()) throw ApiException.validation("Укажите название хроники");
        int rows = jdbc.update("update chronicles set name = ?, session_date = ?, updated_by = ?, updated_at = now() "
                + "where id = ? and campaign_id = ? and deleted = false", request.name.trim(), request.date, userId, id, campaignId);
        if (rows == 0) throw ApiException.notFound("Хроника не найдена");
        return get(campaignId, id, userId);
    }

    @Transactional
    public void delete(UUID campaignId, UUID id, UUID userId) {
        permissions.require(permissions.requireMember(campaignId, userId), Permissions.ENTITY_WRITE);
        jdbc.update("update chronicles set deleted = true where id = ? and campaign_id = ?", id, campaignId);
    }

    @Transactional
    public PointDto addPoint(UUID campaignId, UUID chronicleId, UUID userId, PointRequest request) {
        permissions.require(permissions.requireMember(campaignId, userId), Permissions.ENTITY_WRITE);
        get(campaignId, chronicleId, userId);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into chronicle_points (id, chronicle_id, map_id, hero_id, whole_team, x, y, sort_order, teleport, created_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, chronicleId, UUID.fromString(request.mapId),
                request.wholeTeam || request.heroId == null ? null : UUID.fromString(request.heroId),
                request.wholeTeam, request.x, request.y, request.order, request.teleport, userId);
        return point(chronicleId, id);
    }

    @Transactional
    public void deletePoint(UUID campaignId, UUID chronicleId, UUID pointId, UUID userId) {
        permissions.require(permissions.requireMember(campaignId, userId), Permissions.ENTITY_WRITE);
        jdbc.update("delete from chronicle_points where id = ? and chronicle_id = ?", pointId, chronicleId);
    }

    private PointDto point(UUID chronicleId, UUID id) {
        return jdbc.queryForObject("select p.*, m.name as map_name, h.name as hero_name, h.color as hero_color "
                        + "from chronicle_points p join interactive_maps m on m.id = p.map_id left join heroes h on h.id = p.hero_id "
                        + "where p.id = ? and p.chronicle_id = ?",
                (rs, rowNum) -> {
                    PointDto point = new PointDto();
                    point.id = rs.getString("id");
                    point.chronicleId = rs.getString("chronicle_id");
                    point.mapId = rs.getString("map_id");
                    point.mapName = rs.getString("map_name");
                    point.heroId = rs.getString("hero_id");
                    point.heroName = rs.getString("hero_name");
                    point.heroColor = rs.getString("hero_color");
                    point.wholeTeam = rs.getBoolean("whole_team");
                    point.x = rs.getDouble("x");
                    point.y = rs.getDouble("y");
                    point.order = rs.getInt("sort_order");
                    point.teleport = rs.getBoolean("teleport");
                    return point;
                }, id, chronicleId);
    }
}
