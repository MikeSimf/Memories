package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Invite;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.domain.MemberStatus;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.InviteRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.InviteDtos;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_HOURS = 168;
    private static final int DEFAULT_USES = 1;

    private final InviteRepository inviteRepository;
    private final CampaignRepository campaignRepository;
    private final GameEntityRepository entityRepository;
    private final PermissionService permissionService;
    private final MemberRepository memberRepository;
    private final ChangeRecorder changeRecorder;
    private final QrCodeGenerator qrCodeGenerator;
    private final AppProperties properties;

    @Transactional
    public InviteDtos.InviteDto create(UUID campaignId, UUID actorId, InviteDtos.CreateInviteRequest request) {
        Member actor = permissionService.requireMember(campaignId, actorId);
        permissionService.require(actor, Permissions.INVITE_CREATE);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        return createInternal(campaign, actorId, request.getRole(), request.getLinkedEntityId(),
                request.getExpiresInHours(), request.getMaxUses());
    }

    @Transactional
    public InviteDtos.InviteDto createInternal(Campaign campaign, UUID actorId, MemberRole role,
                                               UUID linkedEntityId, Integer expiresInHours, Integer maxUses) {
        if (role == MemberRole.OWNER) {
            throw ApiException.validation("Нельзя пригласить второго владельца — выберите другую роль");
        }
        Instant now = Instant.now();
        Invite invite = new Invite();
        invite.setId(UUID.randomUUID());
        invite.setCampaignId(campaign.getId());
        invite.setToken(newToken());
        invite.setRole(role);
        invite.setLinkedEntityId(linkedEntityId);
        invite.setCreatedBy(actorId);
        invite.setExpiresAt(now.plus(expiresInHours == null ? DEFAULT_HOURS : expiresInHours, ChronoUnit.HOURS));
        invite.setMaxUses(maxUses == null ? DEFAULT_USES : maxUses);
        invite.setUses(0);
        invite.setCreatedAt(now);
        inviteRepository.save(invite);
        return toDto(invite, campaign.getId());
    }

    @Transactional(readOnly = true)
    public List<InviteDtos.InviteDto> list(UUID campaignId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.INVITE_CREATE);
        List<InviteDtos.InviteDto> result = new ArrayList<>();
        for (Invite invite : inviteRepository.findAllByCampaignIdOrderByCreatedAtDesc(campaignId)) {
            result.add(toDto(invite, campaignId));
        }
        return result;
    }

    @Transactional
    public void revoke(UUID campaignId, UUID inviteId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.INVITE_CREATE);
        Invite invite = inviteRepository.findByIdAndCampaignId(inviteId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Приглашение"));
        invite.setRevoked(true);
        inviteRepository.save(invite);
    }

    /** Публичный предпросмотр приглашения — авторизация не требуется. */
    @Transactional(readOnly = true)
    public InviteDtos.InvitePreviewDto preview(String token) {
        Invite invite = inviteRepository.findByToken(token.trim().toUpperCase())
                .orElseThrow(ApiException::inviteInvalid);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(invite.getCampaignId())
                .orElseThrow(ApiException::inviteInvalid);
        return InviteDtos.InvitePreviewDto.builder()
                .valid(invite.isValid())
                .campaignId(campaign.getId())
                .campaignTitle(campaign.getTitle())
                .role(invite.getRole())
                .linkedEntityId(invite.getLinkedEntityId())
                .linkedEntityName(linkedName(invite.getCampaignId(), invite.getLinkedEntityId()))
                .expiresAt(invite.getExpiresAt())
                .build();
    }

    @Transactional
    public InviteDtos.AcceptInviteResponse accept(String token, UUID userId) {
        Invite invite = inviteRepository.findByToken(token.trim().toUpperCase())
                .orElseThrow(ApiException::inviteInvalid);
        if (!invite.isValid()) {
            throw ApiException.inviteInvalid();
        }
        Campaign campaign = campaignRepository.findForUpdate(invite.getCampaignId())
                .orElseThrow(ApiException::inviteInvalid);

        Member existing = activate(campaign.getId(), userId, invite.getRole(), invite.getLinkedEntityId());
        invite.setUses(invite.getUses() + 1);
        inviteRepository.save(invite);
        changeRecorder.record(campaign, ObjectType.MEMBER, existing.getId(), ChangeOp.UPSERT,
                MemberPayloads.of(existing), userId, null, null);

        return InviteDtos.AcceptInviteResponse.builder()
                .campaignId(campaign.getId())
                .campaignTitle(campaign.getTitle())
                .memberId(existing.getId())
                .role(existing.getRole())
                .linkedEntityId(existing.getLinkedEntityId())
                .build();
    }

    /** Участник становится активным: строка либо создаётся, либо возвращается из состояния REMOVED. */
    private Member activate(UUID campaignId, UUID userId, MemberRole role, UUID linkedEntityId) {
        Member member = memberRepository.findByCampaignIdAndUserId(campaignId, userId).orElse(null);
        if (member == null) {
            member = new Member();
            member.setId(UUID.randomUUID());
            member.setCampaignId(campaignId);
            member.setUserId(userId);
            member.setCreatedAt(Instant.now());
        }
        member.setRole(role);
        if (linkedEntityId != null) {
            member.setLinkedEntityId(linkedEntityId);
        }
        member.setStatus(MemberStatus.ACTIVE);
        member.setUpdatedAt(Instant.now());
        return memberRepository.save(member);
    }

    private InviteDtos.InviteDto toDto(Invite invite, UUID campaignId) {
        String webUrl = trimSlash(properties.getPublicUrl()) + "/join/" + invite.getToken();
        String deepLink = "dndmemory://join?token=" + invite.getToken();
        return InviteDtos.InviteDto.builder()
                .id(invite.getId())
                .token(invite.getToken())
                .url(webUrl)
                .deepLink(deepLink)
                .qrPngBase64(qrCodeGenerator.toPngBase64(webUrl))
                .role(invite.getRole())
                .linkedEntityId(invite.getLinkedEntityId())
                .linkedEntityName(linkedName(campaignId, invite.getLinkedEntityId()))
                .expiresAt(invite.getExpiresAt())
                .maxUses(invite.getMaxUses())
                .uses(invite.getUses())
                .revoked(invite.isRevoked())
                .createdAt(invite.getCreatedAt())
                .build();
    }

    private String linkedName(UUID campaignId, UUID entityId) {
        if (entityId == null) {
            return null;
        }
        return entityRepository.findByIdAndCampaignId(entityId, campaignId)
                .map(GameEntity::getName).orElse(null);
    }

    private String newToken() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String token = sb.toString();
        // Крайне маловероятная коллизия, но проверить дешевле, чем разбираться потом
        return inviteRepository.findByToken(token).isPresent() ? newToken() : token;
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
