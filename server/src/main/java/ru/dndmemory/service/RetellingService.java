package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.domain.RetellingStatus;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.RetellingRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.RetellingDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetellingService {

    private final RetellingRepository retellingRepository;
    private final CampaignRepository campaignRepository;
    private final GameEntityRepository entityRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final EntityService entityService;
    private final ChangeRecorder changeRecorder;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<RetellingDtos.RetellingDto> list(UUID campaignId, UUID userId, String query, UUID authorEntityId) {
        permissionService.requireMember(campaignId, userId);
        Map<String, UUID> index = entityService.nameIndex(campaignId);
        Map<UUID, String> userNames = userNames(campaignId);
        List<RetellingDtos.RetellingDto> result = new ArrayList<>();
        for (Retelling retelling : retellingRepository
                .findAllByCampaignIdAndDeletedFalseOrderByCreatedAtDesc(campaignId)) {
            if (authorEntityId != null && !authorEntityId.equals(retelling.getAuthorEntityId())) {
                continue;
            }
            if (query != null && !query.trim().isEmpty() && !matches(retelling, query)) {
                continue;
            }
            result.add(toDto(retelling, index, userNames));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RetellingDtos.RetellingDto get(UUID campaignId, UUID retellingId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        Retelling retelling = require(campaignId, retellingId);
        return toDto(retelling, entityService.nameIndex(campaignId), userNames(campaignId));
    }

    @Transactional
    public RetellingDtos.RetellingDto create(UUID campaignId, UUID userId, RetellingDtos.CreateRetellingRequest request) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.RETELLING_WRITE);
        rejectDraft(request.getStatus());
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));

        UUID clientId = request.getClientId() != null ? request.getClientId()
                : (request.getId() != null ? request.getId() : UUID.randomUUID());
        Retelling existing = retellingRepository.findByCampaignIdAndClientId(campaignId, clientId).orElse(null);
        Retelling retelling = existing != null ? existing : new Retelling();
        if (existing == null) {
            retelling.setId(request.getId() != null ? request.getId() : UUID.randomUUID());
            retelling.setClientId(clientId);
            retelling.setCampaignId(campaignId);
            retelling.setCreatedBy(userId);
            retelling.setCreatedAt(Instant.now());
        }
        retelling.setTitle(request.getTitle().trim());
        retelling.setBody(request.getBody());
        retelling.setAuthorEntityId(request.getAuthorEntityId());
        retelling.setSessionDate(request.getSessionDate());
        retelling.setStatus(RetellingStatus.PUBLISHED);
        retelling.setUpdatedBy(userId);
        retelling.setUpdatedAt(Instant.now());
        retelling.setDeleted(false);
        retelling.setRevision(campaign.getRevision() + 1);
        retellingRepository.save(retelling);

        RetellingDtos.RetellingDto dto = toDto(retelling, entityService.nameIndex(campaignId), userNames(campaignId));
        changeRecorder.record(campaign, ObjectType.RETELLING, retelling.getId(), ChangeOp.UPSERT, dto, userId, null, null);
        return dto;
    }

    @Transactional
    public RetellingDtos.RetellingDto update(UUID campaignId, UUID retellingId, UUID userId,
                                             RetellingDtos.UpdateRetellingRequest request) {
        Member member = permissionService.requireMember(campaignId, userId);
        Retelling retelling = require(campaignId, retellingId);
        requireEditRight(member, retelling, userId);
        rejectDraft(request.getStatus());
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            retelling.setTitle(request.getTitle().trim());
        }
        if (request.getBody() != null) {
            retelling.setBody(request.getBody());
        }
        if (request.getAuthorEntityId() != null) {
            retelling.setAuthorEntityId(request.getAuthorEntityId());
        }
        if (request.getSessionDate() != null) {
            retelling.setSessionDate(request.getSessionDate());
        }
        retelling.setUpdatedBy(userId);
        retelling.setUpdatedAt(Instant.now());
        retelling.setRevision(campaign.getRevision() + 1);
        retellingRepository.save(retelling);

        RetellingDtos.RetellingDto dto = toDto(retelling, entityService.nameIndex(campaignId), userNames(campaignId));
        changeRecorder.record(campaign, ObjectType.RETELLING, retelling.getId(), ChangeOp.UPSERT, dto, userId, null, null);
        return dto;
    }

    @Transactional
    public void delete(UUID campaignId, UUID retellingId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        Retelling retelling = require(campaignId, retellingId);
        requireEditRight(member, retelling, userId);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        retelling.setDeleted(true);
        retelling.setUpdatedBy(userId);
        retelling.setUpdatedAt(Instant.now());
        retelling.setRevision(campaign.getRevision() + 1);
        retellingRepository.save(retelling);
        changeRecorder.record(campaign, ObjectType.RETELLING, retelling.getId(), ChangeOp.DELETE,
                entityService.tombstone(retelling.getId(), ObjectType.RETELLING,
                        retelling.getRevision(), retelling.getUpdatedAt()),
                userId, null, null);
    }

    @Transactional(readOnly = true)
    public Retelling require(UUID campaignId, UUID retellingId) {
        return retellingRepository.findByIdAndCampaignId(retellingId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Пересказ"));
    }

    /** Править чужой пересказ может только тот, у кого есть retelling.publish. */
    public void requireEditRight(Member member, Retelling retelling, UUID userId) {
        if (retelling.getCreatedBy().equals(userId)) {
            permissionService.require(member, Permissions.RETELLING_WRITE_OWN);
            return;
        }
        permissionService.require(member, Permissions.RETELLING_PUBLISH);
    }

    private void rejectDraft(RetellingStatus status) {
        if (status == RetellingStatus.DRAFT) {
            throw ApiException.draftNotSynced();
        }
    }

    private boolean matches(Retelling retelling, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return contains(retelling.getTitle(), q) || contains(retelling.getBody(), q);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private RetellingDtos.RetellingDto toDto(Retelling retelling, Map<String, UUID> index,
                                             Map<UUID, String> userNames) {
        String authorName = retelling.getAuthorEntityId() == null ? null
                : entityRepository.findById(retelling.getAuthorEntityId()).map(GameEntity::getName).orElse(null);
        return dtoMapper.toRetelling(retelling, index, userNames, authorName);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> userNames(UUID campaignId) {
        List<Retelling> retellings = retellingRepository.findAllByCampaignIdAndDeletedFalseOrderByCreatedAtDesc(campaignId);
        Set<UUID> ids = new HashSet<>();
        for (Retelling retelling : retellings) {
            ids.add(retelling.getCreatedBy());
            ids.add(retelling.getUpdatedBy());
        }
        Map<UUID, String> names = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getDisplayName()));
        }
        return names;
    }
}
