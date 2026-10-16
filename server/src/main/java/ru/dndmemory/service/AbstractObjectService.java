package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.extender.GameEntityExtender;
import ru.dndmemory.domain.extender.ImageExtender;
import ru.dndmemory.domain.extender.NamedExtender;
import ru.dndmemory.domain.extender.SyncExtended;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.ObjectListDto;
import ru.dndmemory.web.dto.SyncDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Общая логика четырёх классов справочника: список, создание, чтение, правка,
 * удаление и применение правок синхронизации. Наследники описывают только своё:
 * таблицу, поля, форму DTO и права на запись.
 *
 * <p>Поля применяются из JSON одним кодом и для REST-запроса, и для записи журнала —
 * поэтому форма payload в синхронизации совпадает с формой DTO своего класса
 * (docs/redesign-v2.md, раздел 3).</p>
 */
public abstract class AbstractObjectService<
        E extends GameEntityExtender & NamedExtender & SyncExtended & ImageExtender, D>
        implements ObjectSyncHandler {

    protected static final int DEFAULT_LIMIT = 50;
    protected static final int MAX_LIMIT = 500;

    protected final CampaignRepository campaignRepository;
    protected final MemberRepository memberRepository;
    protected final PermissionService permissionService;
    protected final ChangeRecorder changeRecorder;
    protected final ObjectIndexService objectIndexService;
    protected final ConflictRecorder conflictRecorder;
    protected final ObjectMapper objectMapper;

    protected AbstractObjectService(CampaignRepository campaignRepository,
                                    MemberRepository memberRepository,
                                    PermissionService permissionService,
                                    ChangeRecorder changeRecorder,
                                    ObjectIndexService objectIndexService,
                                    ConflictRecorder conflictRecorder,
                                    ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.memberRepository = memberRepository;
        this.permissionService = permissionService;
        this.changeRecorder = changeRecorder;
        this.objectIndexService = objectIndexService;
        this.conflictRecorder = conflictRecorder;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------ контракт

    protected abstract GameEntityRepository<E> repository();

    protected abstract E newEntity();

    /** Название класса в сообщениях об ошибках: «Герой», «Персонаж» и т.д. */
    protected abstract String entityLabel();

    protected abstract D toDto(E entity, ObjectIndexService.ObjectContext context);

    /** Перенести поля класса (и общие) из JSON в сущность. В базу ничего не пишет. */
    protected abstract void applyPayload(E entity, JsonNode payload);

    /** Дописать связанные данные после сохранения (у героя — список предметов). */
    protected void afterApply(E entity, JsonNode payload) {
    }

    /** Проверка полей, которые нельзя выразить аннотацией (например, «не более одной ссылки»). */
    protected void validate(E entity) {
    }

    /** Право на создание объекта этого класса. */
    protected void requireCreate(Member member) {
        permissionService.require(member, Permissions.ENTITY_WRITE);
    }

    /** Право на правку и удаление объекта этого класса. */
    protected void requireWrite(Member member, E entity) {
        permissionService.require(member, Permissions.ENTITY_WRITE);
    }

    /** Поиск по названию, описанию и полям класса. */
    protected boolean matchesQuery(E entity, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return contains(entity.getName(), q) || contains(entity.getDescription(), q);
    }

    // --------------------------------------------------------------- REST

    @Transactional(readOnly = true)
    public ObjectListDto<D> list(UUID campaignId, UUID userId, String query, Instant updatedSince,
                                            int limit, int offset) {
        permissionService.requireMember(campaignId, userId);
        List<E> filtered = new ArrayList<>();
        for (E entity : repository().findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
            if (updatedSince != null && !entity.getUpdatedAt().isAfter(updatedSince)) {
                continue;
            }
            if (query != null && !query.trim().isEmpty() && !matchesQuery(entity, query)) {
                continue;
            }
            filtered.add(entity);
        }
        ObjectIndexService.ObjectContext context = objectIndexService.context(campaignId);
        int pageSize = pageSize(limit);
        int from = Math.min(Math.max(offset, 0), filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        List<D> items = new ArrayList<>();
        for (E entity : filtered.subList(from, to)) {
            items.add(toDto(entity, context));
        }
        return new ObjectListDto<>(items, filtered.size());
    }

    @Transactional(readOnly = true)
    public D get(UUID campaignId, UUID objectId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        E entity = require(campaignId, objectId);
        return toDto(entity, objectIndexService.context(campaignId));
    }

    @Transactional
    public D create(UUID campaignId, UUID userId, JsonNode payload, UUID requestedId) {
        Member member = permissionService.requireMember(campaignId, userId);
        requireCreate(member);

        if (requestedId != null) {
            E existing = repository().findByIdAndCampaignId(requestedId, campaignId).orElse(null);
            if (existing != null && !existing.isDeleted()) {
                return toDto(existing, objectIndexService.context(campaignId));
            }
        }
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));

        E entity = newEntity();
        entity.setId(requestedId != null ? requestedId : UUID.randomUUID());
        entity.setCampaignId(campaignId);
        entity.setCreatedBy(userId);
        entity.setCreatedAt(Instant.now());
        applyPayload(entity, payload);
        requireName(entity);
        validate(entity);

        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(Instant.now());
        entity.setDeleted(false);
        entity.setRevision(campaign.getRevision() + 1);
        repository().save(entity);
        afterApply(entity, payload);

        D dto = toDto(entity, objectIndexService.context(campaignId));
        changeRecorder.record(campaign, objectType(), entity.getId(), ChangeOp.UPSERT, dto, userId, null, null);
        return dto;
    }

    @Transactional
    public D update(UUID campaignId, UUID objectId, UUID userId, JsonNode payload, Long ifMatchRevision) {
        Member member = permissionService.requireMember(campaignId, userId);
        E entity = require(campaignId, objectId);
        requireWrite(member, entity);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));

        if (ifMatchRevision != null && ifMatchRevision != entity.getRevision()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("serverRevision", entity.getRevision());
            details.put("server", toDto(entity, objectIndexService.context(campaignId)));
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "conflict",
                    entityLabel() + " изменён другим участником", details);
        }

        applyPayload(entity, payload);
        requireName(entity);
        validate(entity);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(Instant.now());
        entity.setRevision(campaign.getRevision() + 1);
        repository().save(entity);
        afterApply(entity, payload);

        D dto = toDto(entity, objectIndexService.context(campaignId));
        changeRecorder.record(campaign, objectType(), entity.getId(), ChangeOp.UPSERT, dto, userId, null, null);
        return dto;
    }

    @Transactional
    public void delete(UUID campaignId, UUID objectId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        E entity = require(campaignId, objectId);
        requireWrite(member, entity);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        entity.setDeleted(true);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(Instant.now());
        entity.setRevision(campaign.getRevision() + 1);
        repository().save(entity);
        changeRecorder.record(campaign, objectType(), entity.getId(), ChangeOp.DELETE,
                changeRecorder.tombstone(entity.getId(), objectType(), entity.getRevision(), entity.getUpdatedAt()),
                userId, null, null);
    }

    @Transactional(readOnly = true)
    public E require(UUID campaignId, UUID objectId) {
        return repository().findByIdAndCampaignId(objectId, campaignId)
                .orElseThrow(() -> ApiException.notFound(entityLabel()));
    }

    // --------------------------------------------------------- синхронизация

    @Override
    public SyncDtos.PushResultDto applySync(Campaign campaign, Member member, UUID userId, String clientId,
                                            SyncDtos.PushChangeDto change) {
        JsonNode payload = change.getPayload();
        UUID objectId = change.getObjectId();
        E server = repository().findByIdAndCampaignId(objectId, campaign.getId()).orElse(null);

        String status = "APPLIED";
        String matchReason = null;
        // Сначала по id, потом по ключу сопоставления класса: так не появляется дубль
        if (server == null) {
            server = findByMatchKey(campaign.getId(), payload);
            if (server != null) {
                status = "CONFLICT_RESOLVED";
                matchReason = server.matchReason();
            }
        }

        if (change.getOp() == ChangeOp.DELETE) {
            if (server == null || server.isDeleted()) {
                return result(change, objectId, server == null ? null : server.getRevision(), "APPLIED", null);
            }
            try {
                requireWrite(member, server);
            } catch (ApiException e) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
            server.setDeleted(true);
            server.setUpdatedBy(userId);
            server.setUpdatedAt(Instant.now());
            server.setRevision(campaign.getRevision() + 1);
            repository().save(server);
            long revision = changeRecorder.record(campaign, objectType(), server.getId(), ChangeOp.DELETE,
                    changeRecorder.tombstone(server.getId(), objectType(), server.getRevision(), server.getUpdatedAt()),
                    userId, change.getClientChangeId(), clientId);
            return result(change, server.getId(), revision, "APPLIED", null);
        }

        boolean created = server == null;
        if (created) {
            try {
                requireCreate(member);
            } catch (ApiException e) {
                return rejected(campaign, userId, change, "forbidden", null);
            }
            server = newEntity();
            server.setId(objectId);
            server.setCampaignId(campaign.getId());
            server.setCreatedBy(userId);
            server.setCreatedAt(parseInstant(payload, "createdAt", Instant.now()));
        } else {
            try {
                requireWrite(member, server);
            } catch (ApiException e) {
                return rejected(campaign, userId, change, "forbidden", serverPayload(server, campaign.getId()));
            }
        }

        Instant clientUpdatedAt = parseInstant(payload, "updatedAt", null);
        if (!created && change.getBaseRevision() != null && change.getBaseRevision() < server.getRevision()) {
            boolean clientWins = clientUpdatedAt != null && clientUpdatedAt.isAfter(server.getUpdatedAt());
            if (!clientWins) {
                JsonNode serverPayload = serverPayload(server, campaign.getId());
                conflictRecorder.record(campaign, userId, change, "stale_base", serverPayload);
                return result(change, server.getId(), server.getRevision(), "REJECTED",
                        SyncDtos.ConflictInfoDto.builder()
                                .reason("stale_base")
                                .serverRevision(server.getRevision())
                                .server(serverPayload)
                                .build());
            }
            status = "CONFLICT_RESOLVED";
            matchReason = "client_wins";
            conflictRecorder.record(campaign, userId, change, "client_wins", serverPayload(server, campaign.getId()));
        }

        applyPayload(server, payload);
        if (server.getName() == null || server.getName().trim().isEmpty()) {
            return rejected(campaign, userId, change, "validation_failed", null);
        }
        try {
            validate(server);
        } catch (ApiException e) {
            return rejected(campaign, userId, change, "validation_failed", null);
        }
        server.setUpdatedBy(userId);
        server.setUpdatedAt(clientUpdatedAt != null ? clientUpdatedAt : Instant.now());
        server.setDeleted(false);
        server.setRevision(campaign.getRevision() + 1);
        repository().save(server);
        afterApply(server, payload);

        D dto = toDto(server, objectIndexService.context(campaign.getId()));
        long revision = changeRecorder.record(campaign, objectType(), server.getId(), ChangeOp.UPSERT, dto,
                userId, change.getClientChangeId(), clientId);

        SyncDtos.ConflictInfoDto conflict = null;
        if (matchReason != null) {
            conflict = SyncDtos.ConflictInfoDto.builder()
                    .reason(matchReason)
                    .serverRevision(server.getRevision())
                    .build();
            conflictRecorder.record(campaign, userId, change, matchReason, serverPayload(server, campaign.getId()));
        }
        return result(change, server.getId(), revision, status, conflict);
    }

    @Override
    public List<?> snapshotPage(UUID campaignId, int offset, int limit, ObjectIndexService.ObjectContext context) {
        List<E> page = repository()
                .findAllByCampaignIdAndDeletedFalse(campaignId,
                        PageRequest.of(Math.max(offset, 0), pageSize(limit), Sort.by(Sort.Direction.ASC, "name")))
                .getContent();
        List<D> items = new ArrayList<>();
        for (E entity : page) {
            items.add(toDto(entity, context));
        }
        return items;
    }

    @Override
    public long count(UUID campaignId) {
        return repository().countByCampaignIdAndDeletedFalse(campaignId);
    }

    /** Объект той же кампании с таким же ключом сопоставления. */
    protected E findByMatchKey(UUID campaignId, JsonNode payload) {
        if (payload == null || !payload.hasNonNull("name")) {
            return null;
        }
        E probe = newEntity();
        applyPayload(probe, payload);
        String key = probe.matchKey();
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        for (E candidate : repository().findAllByCampaignIdAndDeletedFalse(campaignId)) {
            if (key.equals(candidate.matchKey())) {
                return candidate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------- helpers

    /** Убирает null-поля из запроса: отсутствующее поле не должно затирать значение. */
    protected JsonNode nonNull(Object request) {
        JsonNode node = objectMapper.valueToTree(request);
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            List<String> nulls = new ArrayList<>();
            object.fieldNames().forEachRemaining(field -> {
                if (object.get(field).isNull()) {
                    nulls.add(field);
                }
            });
            nulls.forEach(object::remove);
        }
        return node;
    }

    protected static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /** Разбор необязательного идентификатора из JSON: null или понятная ошибка. */
    protected static UUID uuidOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("Некорректный идентификатор: " + node.asText());
        }
    }

    protected static Instant parseInstant(JsonNode payload, String field, Instant fallback) {
        if (payload == null || !payload.hasNonNull(field)) {
            return fallback;
        }
        try {
            return Instant.parse(payload.get(field).asText());
        } catch (Exception e) {
            return fallback;
        }
    }

    protected static boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    protected static int pageSize(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void requireName(E entity) {
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            throw ApiException.validation("Укажите название");
        }
        entity.setName(entity.getName().trim());
    }

    private JsonNode serverPayload(E entity, UUID campaignId) {
        return objectMapper.valueToTree(toDto(entity, objectIndexService.context(campaignId)));
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
        conflictRecorder.record(campaign, userId, change, reason, serverPayload);
        return result(change, change.getObjectId(), null, "REJECTED",
                SyncDtos.ConflictInfoDto.builder().reason(reason).server(serverPayload).build());
    }
}
