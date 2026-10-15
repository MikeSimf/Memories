package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.domain.MemberStatus;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.repo.RetellingRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.CampaignDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MemberRepository memberRepository;
    private final GameEntityRepository entityRepository;
    private final RetellingRepository retellingRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ChangeRecorder changeRecorder;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<CampaignDtos.CampaignSummaryDto> list(UUID userId) {
        Map<UUID, Member> membership = new HashMap<>();
        for (Member member : memberRepository.findAllByUserId(userId)) {
            if (member.getStatus() != MemberStatus.REMOVED) {
                membership.put(member.getCampaignId(), member);
            }
        }
        if (membership.isEmpty()) {
            return new ArrayList<>();
        }
        List<Campaign> campaigns = campaignRepository.findAllByIdInAndDeletedFalse(new ArrayList<>(membership.keySet()));
        campaigns.sort(Comparator.comparing(Campaign::getUpdatedAt).reversed());
        List<CampaignDtos.CampaignSummaryDto> result = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            result.add(dtoMapper.toCampaignSummary(campaign, membership.get(campaign.getId()),
                    memberRepository.countByCampaignId(campaign.getId()),
                    entityRepository.countByCampaignIdAndDeletedFalse(campaign.getId()),
                    retellingRepository.countByCampaignIdAndDeletedFalse(campaign.getId())));
        }
        return result;
    }

    @Transactional
    public CampaignDtos.CampaignDetailDto create(UUID userId, CampaignDtos.CreateCampaignRequest request) {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setTitle(request.getTitle().trim());
        campaign.setDescription(request.getDescription());
        campaign.setOwnerUserId(userId);
        campaign.setCreatedAt(Instant.now());
        campaign.setUpdatedAt(Instant.now());
        campaignRepository.save(campaign);

        Member owner = new Member();
        owner.setId(UUID.randomUUID());
        owner.setCampaignId(campaign.getId());
        owner.setUserId(userId);
        owner.setRole(MemberRole.OWNER);
        owner.setStatus(MemberStatus.ACTIVE);
        owner.setCreatedAt(Instant.now());
        owner.setUpdatedAt(Instant.now());
        memberRepository.save(owner);

        changeRecorder.record(campaign, ObjectType.CAMPAIGN, campaign.getId(), ChangeOp.UPSERT,
                campaignPayload(campaign), userId, null, null);

        return detail(campaign, owner);
    }

    @Transactional(readOnly = true)
    public CampaignDtos.CampaignDetailDto get(UUID campaignId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        Campaign campaign = requireCampaign(campaignId);
        return detail(campaign, member);
    }

    @Transactional
    public CampaignDtos.CampaignDetailDto update(UUID campaignId, UUID userId, CampaignDtos.UpdateCampaignRequest request) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.CAMPAIGN_MANAGE);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            campaign.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            campaign.setDescription(request.getDescription());
        }
        campaign.setUpdatedAt(Instant.now());
        changeRecorder.record(campaign, ObjectType.CAMPAIGN, campaign.getId(), ChangeOp.UPSERT,
                campaignPayload(campaign), userId, null, null);
        return detail(campaign, member);
    }

    @Transactional
    public void delete(UUID campaignId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.CAMPAIGN_MANAGE);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        campaign.setDeleted(true);
        campaign.setUpdatedAt(Instant.now());
        changeRecorder.record(campaign, ObjectType.CAMPAIGN, campaign.getId(), ChangeOp.DELETE,
                campaignPayload(campaign), userId, null, null);
    }

    @Transactional(readOnly = true)
    public Campaign requireCampaign(UUID campaignId) {
        return campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
    }

    public CampaignDtos.CampaignDetailDto detail(Campaign campaign, Member member) {
        Set<String> permissions = permissionService.permissionSet(member);
        return dtoMapper.toCampaignDetail(campaign, member, permissions,
                memberRepository.countByCampaignId(campaign.getId()),
                entityRepository.countByCampaignIdAndDeletedFalse(campaign.getId()),
                retellingRepository.countByCampaignIdAndDeletedFalse(campaign.getId()));
    }

    public Map<String, Object> campaignPayload(Campaign campaign) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", campaign.getId());
        payload.put("title", campaign.getTitle());
        payload.put("description", campaign.getDescription());
        payload.put("deleted", campaign.isDeleted());
        payload.put("revision", campaign.getRevision());
        payload.put("updatedAt", campaign.getUpdatedAt());
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> memberNames(UUID campaignId) {
        Map<UUID, String> names = new HashMap<>();
        List<Member> members = memberRepository.findAllByCampaignIdOrderByCreatedAtAsc(campaignId);
        List<UUID> userIds = members.stream().map(Member::getUserId).collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                names.put(user.getId(), user.getDisplayName());
            }
        }
        return names;
    }

}
