package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.EntityDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntityService {

    private final GameEntityRepository entityRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ChangeRecorder changeRecorder;
    private final DtoMapper dtoMapper;
    private final ObjectMapper objectMapper;
    private final WikiLinkService wikiLinkService;

    @Transactional(readOnly = true)
    public EntityDtos.EntityListDto list(UUID campaignId, UUID userId, EntityType type, String query,
                                         Instant updatedSince, int limit, int offset) {
        permissionService.requireMember(campaignId, userId);
        List<GameEntity> all = type == null
                ? entityRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)
                : entityRepository.findAllByCampaignIdAndDeletedFalseAndTypeOrderByNameAsc(campaignId, type);

        List<GameEntity> filtered = new ArrayList<>();
        for (GameEntity entity : all) {
            if (updatedSince != null && !entity.getUpdatedAt().isAfter(updatedSince)) {
                continue;
            }
            if (query != null && !query.trim().isEmpty() && !matches(entity, query)) {
                continue;
            }
            filtered.add(entity);
        }
        Map<String, UUID> nameIndex = nameIndex(all);
        Map<UUID, String> userNames = userNames(filtered);

        int from = Math.min(offset, filtered.size());
        int to = Math.min(from + limit, filtered.size());
        List<EntityDtos.EntityDto> items = new ArrayList<>();
        for (GameEntity entity : filtered.subList(from, to)) {
            items.add(dtoMapper.toEntity(entity, nameIndex, userNames));
        }
        return EntityDtos.EntityListDto.builder().items(items).total(filtered.size()).build();
    }

    @Transactional(readOnly = true)
    public EntityDtos.EntityDto get(UUID campaignId, UUID entityId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        GameEntity entity = require(campaignId, entityId);
        return dtoMapper.toEntity(entity, nameIndex(campaignId), userNames(List.of(entity)));
    }

    @Transactional
    public EntityDtos.EntityDto create(UUID campaignId, UUID userId, EntityDtos.CreateEntityRequest request) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.requireEntityCreate(member, request.getType());
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));

        UUID clientId = request.getClientId() != null ? request.getClientId()
                : (request.getId() != null ? request.getId() : UUID.randomUUID());
        GameEntity existing = entityRepository.findByCampaignIdAndClientId(campaignId, clientId).orElse(null);
        if (existing != null && !existing.isDeleted()) {
            return dtoMapper.toEntity(existing, nameIndex(campaignId), userNames(List.of(existing)));
        }

        GameEntity entity = existing != null ? existing : new GameEntity();
        if (existing == null) {
            entity.setId(request.getId() != null ? request.getId() : UUID.randomUUID());
            entity.setClientId(clientId);
            entity.setCampaignId(campaignId);
            entity.setCreatedBy(userId);
            entity.setCreatedAt(Instant.now());
        }
        entity.setType(request.getType());
        entity.setName(request.getName().trim());
        entity.setSummary(request.getSummary());
        entity.setDescription(request.getDescription());
        entity.setAttrs(writeJson(request.getAttrs(), "{}"));
        entity.setTags(writeTags(request.getTags()));
        entity.setImageFileId(request.getImageFileId());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(Instant.now());
        entity.setDeleted(false);
        entity.setRevision(campaign.getRevision() + 1);
        entityRepository.save(entity);

        Map<String, UUID> index = nameIndex(campaignId);
        index.putIfAbsent(entity.getName().toLowerCase(Locale.ROOT), entity.getId());
        EntityDtos.EntityDto dto = dtoMapper.toEntity(entity, index, userNames(List.of(entity)));
        changeRecorder.record(campaign, ObjectType.ENTITY, entity.getId(), ChangeOp.UPSERT, dto, userId, null, null);
        return dto;
    }

    @Transactional
    public EntityDtos.EntityDto update(UUID campaignId, UUID entityId, UUID userId,
                                       EntityDtos.UpdateEntityRequest request, Long ifMatchRevision) {
        Member member = permissionService.requireMember(campaignId, userId);
        GameEntity entity = require(campaignId, entityId);
        permissionService.requireEntityWrite(member, entity);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));

        if (ifMatchRevision != null && ifMatchRevision != entity.getRevision()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("serverRevision", entity.getRevision());
            details.put("server", dtoMapper.toEntity(entity, nameIndex(campaignId), userNames(List.of(entity))));
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "conflict",
                    "Объект изменён другим участником", details);
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            entity.setName(request.getName().trim());
        }
        if (request.getSummary() != null) {
            entity.setSummary(request.getSummary());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getAttrs() != null) {
            entity.setAttrs(writeJson(request.getAttrs(), "{}"));
        }
        if (request.getTags() != null) {
            entity.setTags(writeTags(request.getTags()));
        }
        if (request.getImageFileId() != null) {
            entity.setImageFileId(request.getImageFileId());
        }
        if (request.getType() != null) {
            entity.setType(request.getType());
        }
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(Instant.now());
        entity.setRevision(campaign.getRevision() + 1);
        entityRepository.save(entity);

        EntityDtos.EntityDto dto = dtoMapper.toEntity(entity, nameIndex(campaignId), userNames(List.of(entity)));
        changeRecorder.record(campaign, ObjectType.ENTITY, entity.getId(), ChangeOp.UPSERT, dto, userId, null, null);
        return dto;
    }

    @Transactional
    public void delete(UUID campaignId, UUID entityId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        GameEntity entity = require(campaignId, entityId);
        permissionService.requireEntityWrite(member, entity);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        entity.setDeleted(true);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(Instant.now());
        entity.setRevision(campaign.getRevision() + 1);
        entityRepository.save(entity);
        changeRecorder.record(campaign, ObjectType.ENTITY, entity.getId(), ChangeOp.DELETE,
                tombstone(entity.getId(), ObjectType.ENTITY, entity.getRevision(), entity.getUpdatedAt()),
                userId, null, null);
    }

    @Transactional(readOnly = true)
    public List<EntityDtos.BacklinkDto> backlinks(UUID campaignId, UUID entityId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        require(campaignId, entityId);
        Map<String, UUID> index = nameIndex(campaignId);
        List<EntityDtos.BacklinkDto> result = new ArrayList<>();
        for (GameEntity candidate : entityRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
            if (candidate.getId().equals(entityId)) {
                continue;
            }
            for (WikiLink link : wikiLinks(index, candidate)) {
                if (entityId.equals(link.getTargetId())) {
                    result.add(EntityDtos.BacklinkDto.builder()
                            .id(candidate.getId())
                            .type(candidate.getType())
                            .name(candidate.getName())
                            .snippet(snippet(candidate, link.getLabel()))
                            .build());
                    break;
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GameEntity require(UUID campaignId, UUID entityId) {
        return entityRepository.findByIdAndCampaignId(entityId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Объект"));
    }

    /** Индекс «имя в нижнем регистре» → идентификатор: по нему разрешаются ссылки [[Имя]]. */
    @Transactional(readOnly = true)
    public Map<String, UUID> nameIndex(UUID campaignId) {
        return nameIndex(entityRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId));
    }

    public Map<String, UUID> nameIndex(List<GameEntity> entities) {
        Map<String, UUID> index = new HashMap<>();
        for (GameEntity entity : entities) {
            index.putIfAbsent(entity.getName().toLowerCase(Locale.ROOT), entity.getId());
        }
        return index;
    }

    private List<WikiLink> wikiLinks(Map<String, UUID> index, GameEntity entity) {
        return wikiLinkService.extract(index, entity.getSummary(), entity.getDescription());
    }

    public Map<UUID, String> userNames(List<GameEntity> entities) {
        Set<UUID> ids = new HashSet<>();
        for (GameEntity entity : entities) {
            ids.add(entity.getCreatedBy());
            ids.add(entity.getUpdatedBy());
        }
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User user : userRepository.findAllById(ids)) {
                names.put(user.getId(), user.getDisplayName());
            }
        }
        return names;
    }

    public boolean matches(GameEntity entity, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return contains(entity.getName(), q)
                || contains(entity.getSummary(), q)
                || contains(entity.getDescription(), q)
                || (entity.getTags() != null && entity.getTags().toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String snippet(GameEntity entity, String around) {
        String text = entity.getSummary() != null ? entity.getSummary() : entity.getDescription();
        if (text == null) {
            return null;
        }
        int position = around == null ? -1 : text.toLowerCase(Locale.ROOT).indexOf(around.toLowerCase(Locale.ROOT));
        if (position < 0) {
            position = 0;
        }
        int from = Math.max(0, position - 60);
        int to = Math.min(text.length(), position + 120);
        return (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
    }

    private String writeJson(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        return node.toString();
    }

    private String writeTags(List<String> tags) {
        if (tags == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    public Map<String, Object> tombstone(UUID id, ObjectType type, long revision, Instant updatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("objectType", type);
        payload.put("deleted", true);
        payload.put("revision", revision);
        payload.put("updatedAt", updatedAt);
        return payload;
    }
}
