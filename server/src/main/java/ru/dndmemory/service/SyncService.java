package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeLog;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.domain.RetellingStatus;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.ChangeLogRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.repo.RetellingRepository;
import ru.dndmemory.repo.SyncConflictRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.CampaignDtos;
import ru.dndmemory.web.dto.CharacterDtos;
import ru.dndmemory.web.dto.HeroDtos;
import ru.dndmemory.web.dto.ItemDtos;
import ru.dndmemory.web.dto.LocationDtos;
import ru.dndmemory.web.dto.RetellingDtos;
import ru.dndmemory.web.dto.SyncDtos;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Офлайн-синхронизация по журналу ревизий. Алгоритм и правила конфликтов — docs/sync.md.
 *
 * <p>Правки объектов применяют сервисы четырёх классов: {@link SyncService} только
 * разбирает журнал и отдаёт их нужному обработчику. У каждого класса своя форма payload.</p>
 */
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final int DEFAULT_LIMIT = 500;

    private final CampaignRepository campaignRepository;
    private final ChangeLogRepository changeLogRepository;
    private final RetellingRepository retellingRepository;
    private final MemberRepository memberRepository;
    private final SyncConflictRepository conflictRepository;
    private final PermissionService permissionService;
    private final CampaignService campaignService;
    private final RetellingService retellingService;
    private final ObjectIndexService objectIndexService;
    private final ChangeRecorder changeRecorder;
    private final ConflictRecorder conflictRecorder;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    /** Четыре сервиса классов: ключ — значение ObjectType. */
    private final List<ObjectSyncHandler> handlers;

    private Map<ObjectType, ObjectSyncHandler> handlerMap;

    private Map<ObjectType, ObjectSyncHandler> handlers() {
        if (handlerMap == null) {
            Map<ObjectType, ObjectSyncHandler> map = new EnumMap<>(ObjectType.class);
            for (ObjectSyncHandler handler : handlers) {
                map.put(handler.objectType(), handler);
            }
            handlerMap = map;
        }
        return handlerMap;
    }

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
                                            String clientId, SyncDtos.PushChangeDto change) {
        if (change.getClientChangeId() != null) {
            ChangeLog seen = changeLogRepository
                    .findByCampaignIdAndClientChangeId(campaign.getId(), change.getClientChangeId())
                    .orElse(null);
            if (seen != null) {
                return result(change, seen.getObjectId(), seen.getRevision(), "APPLIED", null);
            }
        }
        if (change.getObjectType() == ObjectType.RETELLING) {
            return applyRetelling(campaign, member, userId, clientId, change);
        }
        ObjectSyncHandler handler = handlers().get(change.getObjectType());
        if (handler == null) {
            // CAMPAIGN и MEMBER клиент не пушит; старый ENTITY больше не поддерживается
            return rejected(campaign, userId, change, "unsupported_type", null);
        }
        return handler.applySync(campaign, member, userId, clientId, change);
    }

    private SyncDtos.PushResultDto applyRetelling(Campaign campaign, Member member, UUID userId,
                                                  String clientId, SyncDtos.PushChangeDto change) {
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
                    changeRecorder.tombstone(server.getId(), ObjectType.RETELLING,
                            server.getRevision(), server.getUpdatedAt()),
                    userId, change.getClientChangeId(), clientId);
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
                conflictRecorder.record(campaign, userId, change, "stale_base", serverPayload);
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

        RetellingDtos.RetellingDto dto = retellingService.toDto(server,
                objectIndexService.context(campaign.getId()), retellingService.userNames(campaign.getId()));
        long revision = changeRecorder.record(campaign, ObjectType.RETELLING, server.getId(), ChangeOp.UPSERT,
                dto, userId, change.getClientChangeId(), clientId);
        return result(change, server.getId(), revision, status, null);
    }

    // ---------------------------------------------------------------- pull

    @Transactional(readOnly = true)
    public SyncDtos.PullResponse pull(UUID campaignId, UUID userId, long since, int limit, String clientId) {
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
            if (clientId != null && clientId.equals(entry.getDeviceId())) {
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

    /**
     * Полная выгрузка кампании страницами по 500 объектов. Порядок классов:
     * герои, персонажи, локации, предметы, пересказы; курсор — «класс:смещение».
     */
    @Transactional(readOnly = true)
    public SyncDtos.SnapshotDto snapshot(UUID campaignId, UUID userId, String cursor, int limit) {
        Member member = permissionService.requireMember(campaignId, userId);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        int pageSize = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        int kindIndex = decodeKind(cursor);
        int offset = decodeOffset(cursor);
        ObjectIndexService.ObjectContext context = objectIndexService.context(campaignId);

        List<HeroDtos.HeroDto> heroes = Collections.emptyList();
        List<CharacterDtos.CharacterDto> characters = Collections.emptyList();
        List<LocationDtos.LocationDto> locations = Collections.emptyList();
        List<ItemDtos.ItemDto> items = Collections.emptyList();
        List<RetellingDtos.RetellingDto> retellings = Collections.emptyList();
        boolean more = false;

        while (true) {
            List<?> page;
            switch (kindIndex) {
                case 0:
                    page = pageOf(ObjectType.HERO, campaignId, offset, pageSize, context);
                    heroes = cast(page);
                    break;
                case 1:
                    page = pageOf(ObjectType.CHARACTER, campaignId, offset, pageSize, context);
                    characters = cast(page);
                    break;
                case 2:
                    page = pageOf(ObjectType.LOCATION, campaignId, offset, pageSize, context);
                    locations = cast(page);
                    break;
                case 3:
                    page = pageOf(ObjectType.ITEM, campaignId, offset, pageSize, context);
                    items = cast(page);
                    break;
                default:
                    page = retellingPage(campaignId, offset, pageSize, context);
                    retellings = cast(page);
                    break;
            }
            if (!page.isEmpty() || kindIndex >= 4) {
                more = page.size() == pageSize
                        || (kindIndex < 4 && hasAnythingAfter(campaignId, kindIndex, offset + page.size(), pageSize));
                break;
            }
            kindIndex++;
            offset = 0;
        }

        CampaignDtos.CampaignDetailDto detail = campaignService.detail(campaign, member);
        return SyncDtos.SnapshotDto.builder()
                .latestRevision(campaign.getRevision())
                .nextCursor(more ? encodeCursor(kindIndex, offset + pageSizeOf(kindIndex, heroes, characters,
                        locations, items, retellings)) : null)
                .campaign(detail)
                .heroes(heroes)
                .characters(characters)
                .locations(locations)
                .items(items)
                .retellings(retellings)
                .members(detail.getMembers())
                .build();
    }

    private List<?> pageOf(ObjectType type, UUID campaignId, int offset, int limit,
                           ObjectIndexService.ObjectContext context) {
        ObjectSyncHandler handler = handlers().get(type);
        return handler == null ? Collections.emptyList() : handler.snapshotPage(campaignId, offset, limit, context);
    }

    private List<RetellingDtos.RetellingDto> retellingPage(UUID campaignId, int offset, int limit,
                                                           ObjectIndexService.ObjectContext context) {
        List<Retelling> page = retellingRepository
                .findAllByCampaignIdAndDeletedFalse(campaignId,
                        PageRequest.of(Math.max(offset, 0), limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        List<RetellingDtos.RetellingDto> result = new ArrayList<>();
        Map<UUID, String> userNames = retellingService.userNames(campaignId);
        for (Retelling retelling : page) {
            result.add(retellingService.toDto(retelling, context, userNames));
        }
        return result;
    }

    /** Есть ли ещё объекты после текущей страницы — чтобы не отдавать пустой курсор. */
    private boolean hasAnythingAfter(UUID campaignId, int kindIndex, int nextOffset, int pageSize) {
        for (int index = kindIndex + 1; index <= 4; index++) {
            ObjectType type = kindOf(index);
            if (type == ObjectType.RETELLING) {
                if (retellingRepository.countByCampaignIdAndDeletedFalse(campaignId) > 0) {
                    return true;
                }
            } else {
                ObjectSyncHandler handler = handlers().get(type);
                if (handler != null && handler.count(campaignId) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ObjectType kindOf(int kindIndex) {
        switch (kindIndex) {
            case 0:
                return ObjectType.HERO;
            case 1:
                return ObjectType.CHARACTER;
            case 2:
                return ObjectType.LOCATION;
            case 3:
                return ObjectType.ITEM;
            default:
                return ObjectType.RETELLING;
        }
    }

    private int pageSizeOf(int kindIndex, List<?> heroes, List<?> characters, List<?> locations,
                           List<?> items, List<?> retellings) {
        switch (kindIndex) {
            case 0:
                return heroes.size();
            case 1:
                return characters.size();
            case 2:
                return locations.size();
            case 3:
                return items.size();
            default:
                return retellings.size();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cast(List<?> page) {
        return (List<T>) page;
    }

    @Transactional(readOnly = true)
    public SyncDtos.SyncStateDto state(UUID campaignId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        long heroes = count(ObjectType.HERO, campaignId);
        long characters = count(ObjectType.CHARACTER, campaignId);
        long locations = count(ObjectType.LOCATION, campaignId);
        long items = count(ObjectType.ITEM, campaignId);
        return SyncDtos.SyncStateDto.builder()
                .latestRevision(campaign.getRevision())
                .minRetainedRevision(changeLogRepository.findMinRevision(campaignId))
                .entityCount(heroes + characters + locations + items)
                .heroCount(heroes)
                .characterCount(characters)
                .locationCount(locations)
                .itemCount(items)
                .retellingCount(retellingRepository.countByCampaignIdAndDeletedFalse(campaignId))
                .memberCount(memberRepository.countByCampaignId(campaignId))
                .pendingConflicts(conflictRepository.countByCampaignId(campaignId))
                .build();
    }

    private long count(ObjectType type, UUID campaignId) {
        ObjectSyncHandler handler = handlers().get(type);
        return handler == null ? 0 : handler.count(campaignId);
    }

    // ------------------------------------------------------------- helpers

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
        conflictRecorder.record(campaign, userId, change, reason, serverPayload);
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

    private String encodeCursor(int kindIndex, int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (kindIndex + ":" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private int decodeKind(String cursor) {
        int[] decoded = decodeCursor(cursor);
        return Math.min(Math.max(decoded[0], 0), 4);
    }

    private int decodeOffset(String cursor) {
        return Math.max(decodeCursor(cursor)[1], 0);
    }

    private int[] decodeCursor(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return new int[]{0, 0};
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    /** Сколько записей журнала хранится — используется задачей компактации. */
    public int retentionMaxRows() {
        return properties.getRetention().getChangeLogMaxRows();
    }
}
