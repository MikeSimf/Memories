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
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.CampaignDtos;
import ru.dndmemory.web.dto.InviteDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final HeroRepository heroRepository;
    private final PermissionService permissionService;
    private final InviteService inviteService;
    private final ChangeRecorder changeRecorder;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<CampaignDtos.MemberDto> list(UUID campaignId, UUID userId) {
        permissionService.requireMember(campaignId, userId);
        List<Member> members = memberRepository.findAllByCampaignIdOrderByCreatedAtAsc(campaignId);
        List<CampaignDtos.MemberDto> result = new ArrayList<>();
        for (Member member : members) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            result.add(dtoMapper.toMember(member, user, linkedHeroName(member.getLinkedHeroId())));
        }
        return result;
    }

    /**
     * Добавляет участника. Если пользователь с такой почтой уже зарегистрирован — он сразу
     * становится участником, иначе создаётся одноразовое приглашение (в ответе поле invite).
     */
    @Transactional
    public CampaignDtos.AddMemberResponse add(UUID campaignId, UUID actorId, CampaignDtos.AddMemberRequest request) {
        Member actor = permissionService.requireMember(campaignId, actorId);
        permissionService.require(actor, Permissions.MEMBERS_MANAGE);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        if (request.getRole() == MemberRole.OWNER) {
            throw ApiException.validation("Роль OWNER назначается только при создании кампании");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(request.getEmail().trim());
        if (existing.isEmpty()) {
            InviteDtos.InviteDto invite = inviteService.createInternal(campaign, actorId, request.getRole(),
                    request.getLinkedHeroId(), null);
            return CampaignDtos.AddMemberResponse.builder()
                    .invite(invite)
                    .note("Пользователь ещё не зарегистрирован — отправьте ему ссылку или QR-код")
                    .build();
        }

        User user = existing.get();
        Member member = memberRepository.findByCampaignIdAndUserId(campaignId, user.getId()).orElse(null);
        if (member != null && member.getStatus() != MemberStatus.REMOVED) {
            throw ApiException.conflict("Этот пользователь уже участвует в кампании");
        }
        if (member == null) {
            member = new Member();
            member.setId(UUID.randomUUID());
            member.setCampaignId(campaignId);
            member.setUserId(user.getId());
            member.setCreatedAt(Instant.now());
        }
        member.setRole(request.getRole());
        member.setLinkedHeroId(request.getLinkedHeroId());
        member.setStatus(MemberStatus.ACTIVE);
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);

        long revision = changeRecorder.record(campaign, ObjectType.MEMBER, member.getId(), ChangeOp.UPSERT,
                MemberPayloads.of(member), actorId, null, null);
        return CampaignDtos.AddMemberResponse.builder()
                .member(dtoMapper.toMember(member, user, linkedHeroName(member.getLinkedHeroId())))
                .note("Участник добавлен (ревизия " + revision + ")")
                .build();
    }

    @Transactional
    public CampaignDtos.MemberDto update(UUID campaignId, UUID memberId, UUID actorId,
                                         CampaignDtos.UpdateMemberRequest request) {
        Member actor = permissionService.requireMember(campaignId, actorId);
        permissionService.require(actor, Permissions.MEMBERS_MANAGE);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        Member member = memberRepository.findByIdAndCampaignId(memberId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Участник"));

        if (request.getRole() != null && request.getRole() != member.getRole()) {
            if (request.getRole() == MemberRole.OWNER) {
                throw ApiException.validation("Передать владение можно отдельным действием");
            }
            guardLastOwner(campaignId, member, "понизить");
            member.setRole(request.getRole());
        }
        if (request.getLinkedHeroId() != null) {
            member.setLinkedHeroId(request.getLinkedHeroId());
        }
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);
        changeRecorder.record(campaign, ObjectType.MEMBER, member.getId(), ChangeOp.UPSERT,
                MemberPayloads.of(member), actorId, null, null);

        User user = userRepository.findById(member.getUserId()).orElse(null);
        return dtoMapper.toMember(member, user, linkedHeroName(member.getLinkedHeroId()));
    }

    /** Передача владения: текущий владелец делает другого участника OWNER и становится мастером. */
    @Transactional
    public CampaignDtos.MemberDto transferOwnership(UUID campaignId, UUID memberId, UUID actorId) {
        Member actor = permissionService.requireMember(campaignId, actorId);
        permissionService.require(actor, Permissions.CAMPAIGN_MANAGE);
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        Member target = memberRepository.findByIdAndCampaignId(memberId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Участник"));
        if (!actor.isOwner()) {
            throw ApiException.forbidden("Передать владение может только создатель сессии");
        }
        if (target.getId().equals(actor.getId())) {
            throw ApiException.validation("Вы уже владелец кампании");
        }
        target.setRole(MemberRole.OWNER);
        target.setUpdatedAt(Instant.now());
        actor.setRole(MemberRole.EDITOR_MASTER);
        actor.setUpdatedAt(Instant.now());
        campaign.setOwnerUserId(target.getUserId());
        campaign.setUpdatedAt(Instant.now());
        memberRepository.save(target);
        memberRepository.save(actor);
        changeRecorder.record(campaign, ObjectType.MEMBER, target.getId(), ChangeOp.UPSERT,
                MemberPayloads.of(target), actorId, null, null);
        changeRecorder.record(campaign, ObjectType.MEMBER, actor.getId(), ChangeOp.UPSERT,
                MemberPayloads.of(actor), actorId, null, null);

        User user = userRepository.findById(target.getUserId()).orElse(null);
        return dtoMapper.toMember(target, user, linkedHeroName(target.getLinkedHeroId()));
    }

    @Transactional
    public void remove(UUID campaignId, UUID memberId, UUID actorId) {
        Member actor = permissionService.requireMember(campaignId, actorId);
        Member member = memberRepository.findByIdAndCampaignId(memberId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Участник"));
        if (!actor.getId().equals(member.getId())) {
            permissionService.require(actor, Permissions.MEMBERS_MANAGE);
        }
        guardLastOwner(campaignId, member, "удалить");
        Campaign campaign = campaignRepository.findForUpdate(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        member.setStatus(MemberStatus.REMOVED);
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);
        changeRecorder.record(campaign, ObjectType.MEMBER, member.getId(), ChangeOp.DELETE,
                MemberPayloads.of(member), actorId, null, null);
    }

    private void guardLastOwner(UUID campaignId, Member member, String action) {
        if (member.isOwner() && memberRepository.countByCampaignIdAndRole(campaignId, MemberRole.OWNER) <= 1) {
            throw ApiException.conflict("Нельзя " + action + " единственного владельца кампании");
        }
    }

    private String linkedHeroName(UUID heroId) {
        if (heroId == null) {
            return null;
        }
        return heroRepository.findById(heroId).map(hero -> hero.getName()).orElse(null);
    }

    public Map<String, Object> memberPayload(Member member) {
        return MemberPayloads.of(member);
    }
}
