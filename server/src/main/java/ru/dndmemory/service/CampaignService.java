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
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.CharacterRepository;
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.ItemRepository;
import ru.dndmemory.repo.LocationRepository;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MemberRepository memberRepository;
    private final HeroRepository heroRepository;
    private final CharacterRepository characterRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;
    private final RetellingRepository retellingRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ChangeRecorder changeRecorder;
    private final DtoMapper dtoMapper;
    private final ObjectIndexService objectIndexService;

    @Transactional(readOnly = true)
    public List<CampaignDtos.CampaignSummaryDto> list(UUID userId) {
        if (userRepository.findById(userId).map(u -> u.isSuperuser()).orElse(false)) {
            List<Campaign> all = campaignRepository.findAllByDeletedFalseOrderByUpdatedAtDesc();
            List<CampaignDtos.CampaignSummaryDto> result = new ArrayList<>();
            for (Campaign campaign : all) {
                Member synthetic = new Member();
                synthetic.setId(new UUID(0L, 0L));
                synthetic.setCampaignId(campaign.getId());
                synthetic.setUserId(userId);
                synthetic.setRole(ru.dndmemory.domain.MemberRole.OWNER);
                synthetic.setStatus(ru.dndmemory.domain.MemberStatus.ACTIVE);
                result.add(dtoMapper.toCampaignSummary(campaign, synthetic,
                        memberRepository.countByCampaignId(campaign.getId()),
                        entityCount(campaign.getId()),
                        retellingRepository.countByCampaignIdAndDeletedFalse(campaign.getId())));
            }
            return result;
        }
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
                    entityCount(campaign.getId()),
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
        // ВАЖНО: у сущности уже задан @Id, поэтому Spring Data вызывает merge и возвращает
        // УПРАВЛЯЕМУЮ копию. Исходный объект остаётся отсоединённым, и правки после save()
        // (в частности увеличение revision в ChangeRecorder) до базы не доходят.
        campaign = campaignRepository.save(campaign);

        Member owner = new Member();
        owner.setId(UUID.randomUUID());
        owner.setCampaignId(campaign.getId());
        owner.setUserId(userId);
        owner.setRole(MemberRole.OWNER);
        owner.setStatus(MemberStatus.ACTIVE);
        owner.setCreatedAt(Instant.now());
        owner.setUpdatedAt(Instant.now());
        owner = memberRepository.save(owner);

        // ссылка нужна финальной: payload для журнала собирается в лямбде
        Campaign created = campaign;
        changeRecorder.record(created, ObjectType.CAMPAIGN, created.getId(), ChangeOp.UPSERT,
                () -> campaignPayload(created), userId, null, null);

        return detail(created, owner);
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
                () -> campaignPayload(campaign), userId, null, null);
        return detail(campaign, member);
    }

    @Transactional
    public void delete(UUID campaignId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.CAMPAIGN_DELETE);
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

    /** Карточка кампании: счётчики, краткий список участников и краткий список пересказов. */
    public CampaignDtos.CampaignDetailDto detail(Campaign campaign, Member member) {
        Set<String> permissions = permissionService.permissionSet(member);
        ObjectIndexService.ObjectContext context = objectIndexService.context(campaign.getId());
        return dtoMapper.toCampaignDetail(campaign, member, permissions,
                memberRepository.countByCampaignId(campaign.getId()),
                entityCount(campaign.getId()),
                retellingRepository.countByCampaignIdAndDeletedFalse(campaign.getId()),
                memberDtos(campaign.getId(), context),
                retellingBriefs(campaign.getId()));
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

    /** Участники кампании кратким списком для карточки. */
    @Transactional(readOnly = true)
    public List<CampaignDtos.MemberDto> memberDtos(UUID campaignId, ObjectIndexService.ObjectContext context) {
        List<Member> members = memberRepository.findAllByCampaignIdOrderByCreatedAtAsc(campaignId);
        Set<UUID> userIds = new HashSet<>();
        members.forEach(m -> userIds.add(m.getUserId()));
        Map<UUID, User> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> users.put(user.getId(), user));
        }
        List<CampaignDtos.MemberDto> result = new ArrayList<>();
        for (Member member : members) {
            result.add(dtoMapper.toMember(member, users.get(member.getUserId()),
                    context.nameOf(member.getLinkedHeroId())));
        }
        return result;
    }

    /** Пересказы кратким списком: полный текст в карточке кампании не нужен. */
    @Transactional(readOnly = true)
    public List<CampaignDtos.RetellingBriefDto> retellingBriefs(UUID campaignId) {
        List<CampaignDtos.RetellingBriefDto> result = new ArrayList<>();
        for (Retelling retelling : retellingRepository
                .findAllByCampaignIdAndDeletedFalseOrderByCreatedAtDesc(campaignId)) {
            result.add(CampaignDtos.RetellingBriefDto.builder()
                    .id(retelling.getId())
                    .title(retelling.getTitle())
                    .sessionDate(retelling.getSessionDate())
                    .revision(retelling.getRevision())
                    .updatedAt(retelling.getUpdatedAt())
                    .build());
        }
        return result;
    }

    /** Сколько всего объектов четырёх классов в кампании. */
    public long entityCount(UUID campaignId) {
        return heroRepository.countByCampaignIdAndDeletedFalse(campaignId)
                + characterRepository.countByCampaignIdAndDeletedFalse(campaignId)
                + locationRepository.countByCampaignIdAndDeletedFalse(campaignId)
                + itemRepository.countByCampaignIdAndDeletedFalse(campaignId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> memberNames(UUID campaignId) {
        Map<UUID, String> names = new HashMap<>();
        List<Member> members = memberRepository.findAllByCampaignIdOrderByCreatedAtAsc(campaignId);
        List<UUID> userIds = new ArrayList<>();
        members.forEach(member -> userIds.add(member.getUserId()));
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                names.put(user.getId(), user.getDisplayName());
            }
        }
        return names;
    }
}
