package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeLog;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.domain.RetellingStatus;
import ru.dndmemory.domain.SyncConflict;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.ChangeLogRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.repo.RetellingRepository;
import ru.dndmemory.repo.SyncConflictRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.CampaignDtos;
import ru.dndmemory.web.dto.EntityDtos;
import ru.dndmemory.web.dto.RetellingDtos;
import ru.dndmemory.web.dto.SyncDtos;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Офлайн-синхронизация по журналу ревизий. Алгоритм и правила конфликтов — docs/sync.md.
 */
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int DEFAULT_LIMIT = 500;

    private final CampaignRepository campaignRepository;
    private final ChangeLogRepository changeLogRepository;
    private final GameEntityRepository entityRepository;
    private final RetellingRepository retellingRepository;
    private final MemberRepository memberRepository;
    private final SyncConflictRepository conflictRepository;
    private final PermissionService permissionService;
    private final EntityService entityService;
    private final RetellingService retellingService;
    private final ChangeRecorder changeRecorder;
    private final DtoMapper dtoMapper;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    // ---------------------------------------------------------------- push

    @Transactional
    public SyncDtos.PushResponse push(UUID campaignId, UUID userId, SyncDtos.PushRequest request) {
        Member member = permissionService.requireMember(campaignId, userId);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        List<SyncDtos.PushResultDto> results = new ArrayList<>();
        if (request.getChanges() != null) {
            for (SyncDtos.PushChangeDto change : request.getChanges()) {
                results.add(applyOne(campaign, member, userId, request.getClientId(), change));
            }
        }
        return SyncDtos.PushResponse.builder()
                .results(results)
                .latestRevision(campaign.getRevision())
                .build();
    }

    private SyncDtos.PushResultDto applyOne(Campaign campaign, Member member, UUID userId,
                                            String deviceId, SyncDtos.PushChangeDto change) {
        if (change.getClientChangeId() != null) {
            ChangeLog seen = changeLogRepository
                    .findByCampaignIdAndClientChangeId(campaign.getId(), change.getClientChangeId())
                    .orElse(null);
            if (seen != null) {
                return result(change, seen.getObjectId(), seen.getRevision(), "APPLIED", null);
            }
        }
        if (change.getObjectType() == ObjectType.ENTITY) {
            return applyEntity(campaign, member, userId, deviceId, change);
        }
        if (change.getObjectType() == ObjectType.RETELLING) {
            return applyRetelling(campaign, member, userId, deviceId, change);
        }
        return rejected(campaign, userId, change, "unsupported_type", null);
    }

    private SyncDtos.PushResultDto applyEntity(Campaign campaign, Member member, UUID userId,
                                               String deviceId, SyncDtos.PushChangeDto change) {
        JsonNode payload = change.getPayload();
        UUID objectId = change.getObjectId();
        GameEntity server = entityRepository.findByIdAndCampaignId(objectId, campaign.getId()).orElse(null);
        if (server == null && payload != null && payload.hasNonNull("clientId")) {
            server = entityRepository.findByCampaignIdAndClientId(campaign.getId(),
                    UUID.fromString(payload.get("clientId").asText())).orElse(null);
        }

        if (change.getOp() == ChangeOp.DELETE) {
            if (server == null || server.isDeleted()) {
                return result(change, objectId, server == null ? null : server.getRevision(), "APPLIED", null);
            }
            if (!permissionService.canWriteEntity(member, server.getId(), server.getType())) {
                return rejected(campaign, userId, change, "forbidden", objectMapper.valueToTree(
                        dtoMapper.toEntity(server, entityService.nameIndex(campaign.getId()), Collections.emptyMap())));
            }
            server.setDeleted(true);
            server.setUpdatedBy(userId);
            server.setUpdatedAt(Instant.now());
            server.setRevision(campaign.getRevision() + 1);
            entityRepository.save(server);
            long revision = changeRecorder.record(campaign, ObjectType.ENTITY, server.getId(), ChangeOp.DELETE,
                    entityService.tombstone(server.getId(), ObjectType.ENTITY, server.getRevision(), server.getUpdatedAt()),
                    userId, change.getClientChangeId(), deviceId);
            return result(change, server.getId(), revision, "APPLIED", null);
        }

        EntityType type = server != null ? server.getType()
                : parseEnum(payload, "type", EntityType.class, EntityType.ITEM);
        boolean created = server == null;
        if (created) {
            if (member.getRole() == ru.dndmemory.domain.MemberRole.VIEWER) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
            if (type == EntityType.HERO && member.getRole() == ru.dndmemory.domain.MemberRole.EDITOR_HERO) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
            server = new GameEntity();
            server.setId(objectId);
            server.setCampaignId(campaign.getId());
            server.setClientId(payload != null && payload.hasNonNull("clientId")
                    ? UUID.fromString(payload.get("clientId").asText()) : objectId);
            server.setCreatedBy(userId);
            server.setCreatedAt(parseInstant(payload, "createdAt", Instant.now()));
        } else if (!permissionService.canWriteEntity(member, server.getId(), server.getType())) {
            return rejected(campaign, userId, change, "forbidden", objectMapper.valueToTree(
                        dtoMapper.toEntity(server, entityService.nameIndex(campaign.getId()), Collections.emptyMap())));
        }

        String status = "APPLIED";
        Instant clientUpdatedAt = parseInstant(payload, "updatedAt", null);
        if (!created && change.getBaseRevision() != null && change.getBaseRevision() < server.getRevision()) {
            boolean clientWins = clientUpdatedAt != null && clientUpdatedAt.isAfter(server.getUpdatedAt());
            if (!clientWins) {
                JsonNode serverPayload = objectMapper.valueToTree(dtoMapper.toEntity(server,
                        entityService.nameIndex(campaign.getId()), Collections.emptyMap()));
                recordConflict(campaign, userId, change, "stale_base", serverPayload);
                SyncDtos.ConflictInfoDto conflict = SyncDtos.ConflictInfoDto.builder()
                        .reason("stale_base")
                        .serverRevision(server.getRevision())
                        .server(serverPayload)
                        .build();
                return result(change, server.getId(), server.getRevision(), "REJECTED", conflict);
            }
            status = "CONFLICT_RESOLVED";
            recordConflict(campaign, userId, change, "client_wins",
                    objectMapper.valueToTree(dtoMapper.toEntity(server, entityService.nameIndex(campaign.getId()),
                            Collections.emptyMap())));
        }

        if (payload != null) {
            if (payload.hasNonNull("name")) {
                server.setName(payload.get("name").asText());
            }
            if (payload.has("summary")) {
                server.setSummary(text(payload.get("summary")));
            }
            if (payload.has("description")) {
                server.setDescription(text(payload.get("description")));
            }
            if (payload.has("attrs")) {
                server.setAttrs(payload.get("attrs").isNull() ? "{}" : payload.get("attrs").toString());
            }
            if (payload.has("tags") && payload.get("tags").isArray()) {
                server.setTags(payload.get("tags").toString());
            }
            if (payload.hasNonNull("type")) {
                server.setType(EntityType.valueOf(payload.get("type").asText()));
            }
            if (payload.hasNonNull("imageFileId")) {
                server.setImageFileId(UUID.fromString(payload.get("imageFileId").asText()));
            }
        }
        if (server.getAttrs() == null) {
            server.setAttrs("{}");
        }
        if (server.getTags() == null) {
            server.setTags("[]");
        }
        if (server.getName() == null || server.getName().trim().isEmpty()) {
            return rejected(campaign, userId, change, "validation_failed", null);
        }
        server.setUpdatedBy(userId);
        server.setUpdatedAt(clientUpdatedAt != null ? clientUpdatedAt : Instant.now());
        server.setDeleted(false);
        server.setRevision(campaign.getRevision() + 1);
        entityRepository.save(server);

        EntityDtos.EntityDto dto = dtoMapper.toEntity(server, entityService.nameIndex(campaign.getId()),
                entityService.userNames(Collections.singletonList(server)));
        long revision = changeRecorder.record(campaign, ObjectType.ENTITY, server.getId(), ChangeOp.UPSERT,
                dto, userId, change.getClientChangeId(), deviceId);
        return result(change, server.getId(), revision, status, null);
    }

    private SyncDtos.PushResultDto applyRetelling(Campaign campaign, Member member, UUID userId,
                                                  String deviceId, SyncDtos.PushChangeDto change) {
        JsonNode payload = change.getPayload();
        UUID objectId = change.getObjectId();
        Retelling server = retellingRepository.findByIdAndCampaignId(objectId, campaign.getId()).orElse(null);

        if (change.getOp() == ChangeOp.DELETE) {
            if (server == null || server.isDeleted()) {
                return result(change, objectId, server == null ? null : server.getRevision(), "APPLIED", null);
            }
            try {
                retellingService.requireEditRight(member, server, userId);
            } catch (ApiException e) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
            server.setDeleted(true);
            server.setUpdatedBy(userId);
            server.setUpdatedAt(Instant.now());
            server.setRevision(campaign.getRevision() + 1);
            retellingRepository.save(server);
            long revision = changeRecorder.record(campaign, ObjectType.RETELLING, server.getId(), ChangeOp.DELETE,
                    entityService.tombstone(server.getId(), ObjectType.RETELLING, server.getRevision(), server.getUpdatedAt()),
                    userId, change.getClientChangeId(), deviceId);
            return result(change, server.getId(), revision, "APPLIED", null);
        }

        if (payload != null && payload.hasNonNull("status")
                && "DRAFT".equalsIgnoreCase(payload.get("status").asText())) {
            return rejected(campaign, userId, change, "draft_not_synced", null);
        }

        boolean created = server == null;
        if (created) {
            if (member.getRole() == ru.dndmemory.domain.MemberRole.VIEWER) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
            server = new Retelling();
            server.setId(objectId);
            server.setCampaignId(campaign.getId());
            server.setClientId(payload != null && payload.hasNonNull("clientId")
                    ? UUID.fromString(payload.get("clientId").asText()) : objectId);
            server.setCreatedBy(userId);
            server.setCreatedAt(parseInstant(payload, "createdAt", Instant.now()));
        } else {
            try {
                retellingService.requireEditRight(member, server, userId);
            } catch (ApiException e) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
        }

        String status = "APPLIED";
        Instant clientUpdatedAt = parseInstant(payload, "updatedAt", null);
        if (!created && change.getBaseRevision() != null && change.getBaseRevision() < server.getRevision()) {
            boolean clientWins = clientUpdatedAt != null && clientUpdatedAt.isAfter(server.getUpdatedAt());
            if (!clientWins) {
                JsonNode serverPayload = objectMapper.valueToTree(server);
                recordConflict(campaign, userId, change, "stale_base", serverPayload);
                return result(change, server.getId(), server.getRevision(), "REJECTED",
                        SyncDtos.ConflictInfoDto.builder().reason("stale_base")
                                .serverRevision(server.getRevision()).server(serverPayload).build());
            }
            status = "CONFLICT_RESOLVED";
        }

        if (payload != null) {
            if (payload.hasNonNull("title")) {
                server.setTitle(payload.get("title").asText());
            }
            if (payload.has("body")) {
                server.setBody(text(payload.get("body")));
            }
            if (payload.hasNonNull("authorEntityId")) {
                server.setAuthorEntityId(UUID.fromString(payload.get("authorEntityId").asText()));
            }
            if (payload.hasNonNull("sessionDate")) {
                server.setSessionDate(LocalDate.parse(payload.get("sessionDate").asText()));
            }
        }
        if (server.getTitle() == null || server.getTitle().trim().isEmpty()) {
            return rejected(campaign, userId, change, "validation_failed", null);
        }
        server.setStatus(RetellingStatus.PUBLISHED);
        server.setUpdatedBy(userId);
        server.setUpdatedAt(clientUpdatedAt != null ? clientUpdatedAt : Instant.now());
        server.setDeleted(false);
        server.setRevision(campaign.getRevision() + 1);
        retellingRepository.save(server);

        RetellingDtos.RetellingDto dto = dtoMapper.toRetelling(server, entityService.nameIndex(campaign.getId()),
                retellingService.userNames(campaign.getId()),
                server.getAuthorEntityId() == null ? null : entityRepository.findById(server.getAuthorEntityId())
                        .map(GameEntity::getName).orElse(null));
        long revision = changeRecorder.record(campaign, ObjectType.RETELLING, server.getId(), ChangeOp.UPSERT,
                dto, userId, change.getClientChangeId(), deviceId);
        return result(change, server.getId(), revision, status, null);
    }

    // ---------------------------------------------------------------- pull

    @Transactional(readOnly = true)
    public SyncDtos.PullResponse pull(UUID campaignId, UUID userId, long since, int limit, String deviceId) {
        permissionService.requireMember(campaignId, userId);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        Long minRevision = changeLogRepository.findMinRevision(campaignId);
        if (minRevision != null && since > 0 && since < minRevision - 1) {
            throw ApiException.resyncRequired();
        }
        int pageSize = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        List<ChangeLog> raw = changeLogRepository.findAllByCampaignIdAndRevisionGreaterThanOrderByRevisionAsc(
                campaignId, since, PageRequest.of(0, pageSize + 1));

        boolean hasMore = raw.size() > pageSize;
        List<ChangeLog> page = hasMore ? raw.subList(0, pageSize) : raw;

        List<SyncDtos.ChangeDto> changes = new ArrayList<>();
        long lastRevision = since;
        for (ChangeLog entry : page) {
            lastRevision = Math.max(lastRevision, entry.getRevision());
            // свои же изменения клиенту не возвращаем — он их уже применил локально
            if (deviceId != null && deviceId.equals(entry.getDeviceId())) {
                continue;
            }
            changes.add(SyncDtos.ChangeDto.builder()
                    .revision(entry.getRevision())
                    .objectType(entry.getObjectType())
                    .objectId(entry.getObjectId())
                    .op(entry.getOp())
                    .payload(readJson(entry.getPayload()))
                    .actorUserId(entry.getActorUserId())
                    .clientChangeId(entry.getClientChangeId())
                    .createdAt(entry.getCreatedAt())
                    .build());
        }
        return SyncDtos.PullResponse.builder()
                .since(since)
                .latestRevision(hasMore ? lastRevision : campaign.getRevision())
                .hasMore(hasMore)
                .changes(changes)
                .build();
    }

    // ------------------------------------------------------------ snapshot

    @Transactional(readOnly = true)
    public SyncDtos.SnapshotDto snapshot(UUID campaignId, UUID userId, String cursor, int limit) {
        Member member = permissionService.requireMember(campaignId, userId);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        int pageSize = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        int[] offsets = decodeCursor(cursor);

        List<GameEntity> entities = entityRepository
                .findAllByCampaignIdAndDeletedFalse(campaignId, PageRequest.of(offsets[0], pageSize))
                .getContent();
        List<Retelling> retellings = offsets[0] == 0
                ? retellingRepository.findAllByCampaignIdAndDeletedFalse(campaignId, PageRequest.of(offsets[1], pageSize))
                .getContent()
                : Collections.emptyList();

        Map<String, UUID> nameIndex = entityService.nameIndex(campaignId);
        Map<UUID, String> userNames = entityService.userNames(
                entityRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId));

        List<EntityDtos.EntityDto> entityDtos = new ArrayList<>();
        for (GameEntity entity : entities) {
            entityDtos.add(dtoMapper.toEntity(entity, nameIndex, userNames));
        }
        List<RetellingDtos.RetellingDto> retellingDtos = new ArrayList<>();
        for (Retelling retelling : retellings) {
            String authorName = retelling.getAuthorEntityId() == null ? null
                    : entityRepository.findById(retelling.getAuthorEntityId()).map(GameEntity::getName).orElse(null);
            retellingDtos.add(dtoMapper.toRetelling(retelling, nameIndex, retellingService.userNames(campaignId), authorName));
        }

        int nextEntityOffset = offsets[0] + entities.size();
        long totalEntities = entityRepository.countByCampaignIdAndDeletedFalse(campaignId);
        boolean more = nextEntityOffset < totalEntities;

        List<CampaignDtos.MemberDto> members = offsets[0] == 0
                ? memberDtos(campaignId) : Collections.emptyList();

        return SyncDtos.SnapshotDto.builder()
                .latestRevision(campaign.getRevision())
                .nextCursor(more ? encodeCursor(nextEntityOffset, 0, 0) : null)
                                .campaign(dtoMapper.toCampaignDetail(campaign, member, permissionService.permissionSet(member),
                        memberRepository.countByCampaignId(campaignId),
                        entityRepository.countByCampaignIdAndDeletedFalse(campaignId),
                        retellingRepository.countByCampaignIdAndDeletedFalse(campaignId)))
                .entities(entityDtos)
                .retellings(retellingDtos)
                .members(members)
                .build();
    }

    @Transactional(readOnly = true)
    public SyncDtos.SyncStateDto state(UUID campaignId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        return SyncDtos.SyncStateDto.builder()
                .latestRevision(campaign.getRevision())
                .minRetainedRevision(changeLogRepository.findMinRevision(campaignId))
                .entityCount(entityRepository.countByCampaignIdAndDeletedFalse(campaignId))
                .retellingCount(retellingRepository.countByCampaignIdAndDeletedFalse(campaignId))
                .memberCount(memberRepository.countByCampaignId(campaignId))
                .pendingConflicts(conflictRepository.countByCampaignId(campaignId))
                .build();
    }

    // ------------------------------------------------------------- helpers

    private List<CampaignDtos.MemberDto> memberDtos(UUID campaignId) {
        List<CampaignDtos.MemberDto> members = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        for (Member member : memberRepository.findAllByCampaignIdOrderByCreatedAtAsc(campaignId)) {
            names.putIfAbsent(member.getUserId(), null);
            String linkedName = member.getLinkedEntityId() == null ? null
                    : entityRepository.findByIdAndCampaignId(member.getLinkedEntityId(), campaignId)
                    .map(GameEntity::getName).orElse(null);
            CampaignDtos.MemberDto dto = CampaignDtos.MemberDto.builder()
                    .id(member.getId())
                    .userId(member.getUserId())
                    .role(member.getRole())
                    .linkedEntityId(member.getLinkedEntityId())
                    .linkedEntityName(linkedName)
                    .status(member.getStatus().name())
                    .createdAt(member.getCreatedAt())
                    .build();
            members.add(dto);
        }
        return members;
    }

    private void recordConflict(Campaign campaign, UUID userId, SyncDtos.PushChangeDto change,
                                String reason, JsonNode serverPayload) {
        SyncConflict conflict = new SyncConflict();
        conflict.setId(UUID.randomUUID());
        conflict.setCampaignId(campaign.getId());
        conflict.setObjectType(change.getObjectType());
        conflict.setObjectId(change.getObjectId());
        conflict.setClientChangeId(change.getClientChangeId());
        conflict.setActorUserId(userId);
        conflict.setReason(reason);
        conflict.setServerPayload(serverPayload == null ? null : serverPayload.toString());
        conflict.setClientPayload(change.getPayload() == null ? null : change.getPayload().toString());
        conflictRepository.save(conflict);
    }

    private SyncDtos.PushResultDto result(SyncDtos.PushChangeDto change, UUID objectId, Long revision,
                                          String status, SyncDtos.ConflictInfoDto conflict) {
        return SyncDtos.PushResultDto.builder()
                .clientChangeId(change.getClientChangeId())
                .objectId(objectId)
                .revision(revision)
                .status(status)
                .conflict(conflict)
                .build();
    }

    private SyncDtos.PushResultDto rejected(Campaign campaign, UUID userId, SyncDtos.PushChangeDto change,
                                            String reason, JsonNode serverPayload) {
        recordConflict(campaign, userId, change, reason, serverPayload);
        return result(change, change.getObjectId(), null, "REJECTED",
                SyncDtos.ConflictInfoDto.builder().reason(reason).server(serverPayload).build());
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private Instant parseInstant(JsonNode payload, String field, Instant fallback) {
        if (payload == null || !payload.hasNonNull(field)) {
            return fallback;
        }
        try {
            return Instant.parse(payload.get(field).asText());
        } catch (Exception e) {
            return fallback;
        }
    }

    private <T extends Enum<T>> T parseEnum(JsonNode payload, String field, Class<T> type, T fallback) {
        if (payload == null || !payload.hasNonNull(field)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, payload.get(field).asText());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String encodeCursor(int entityOffset, int retellingOffset, int memberOffset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (entityOffset + ":" + retellingOffset + ":" + memberOffset).getBytes(StandardCharsets.UTF_8));
    }

    private int[] decodeCursor(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return new int[]{0, 0, 0};
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (Exception e) {
            return new int[]{0, 0, 0};
        }
    }

    /** Сколько записей журнала хранится — используется задачей компактации. */
    public int retentionMaxRows() {
        return properties.getRetention().getChangeLogMaxRows();
    }
}
