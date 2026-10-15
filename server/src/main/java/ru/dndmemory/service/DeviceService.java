package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.AuthProvider;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.Device;
import ru.dndmemory.domain.Invite;
import ru.dndmemory.domain.InviteMode;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.DeviceRepository;
import ru.dndmemory.repo.InviteRepository;
import ru.dndmemory.repo.RefreshTokenRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.AuthDtos;
import ru.dndmemory.web.dto.DeviceDtos;
import ru.dndmemory.web.dto.InviteDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Регистрация устройства по QR-коду.
 *
 * Админ выпускает QR для конкретного игрока (имя, роль, привязка к герою). Телефон
 * сканирует код и получает долгоживущую сессию — пароль и SMS не нужны. Позже в
 * профиле человек указывает почту и телефон и задаёт пароль, чтобы входить в
 * веб-версию (см. AuthService.setPassword).
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final InviteRepository inviteRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final InviteService inviteService;
    private final MemberService memberService;
    private final PermissionService permissionService;
    private final ChangeRecorder changeRecorder;
    private final AuthService authService;

    /** Шаг админа: выпустить QR-код устройства. */
    @Transactional
    public InviteDtos.InviteDto createEnrollment(UUID campaignId, UUID actorId,
                                                 DeviceDtos.CreateEnrollmentRequest request) {
        Member actor = permissionService.requireMember(campaignId, actorId);
        permissionService.require(actor, Permissions.MEMBERS_MANAGE);
        Campaign campaign = campaignRepository.findByIdAndDeletedFalse(campaignId)
                .orElseThrow(() -> ApiException.notFound("Кампания"));
        return inviteService.createInternal(campaign, actorId, request.getRole(), request.getLinkedEntityId(),
                request.getExpiresInHours(), request.getMaxUses(), InviteMode.DEVICE,
                request.getDisplayName().trim(), request.getLabel());
    }

    /** Шаг телефона: обменять токен из QR на сессию. Авторизация не нужна — токен и есть пропуск. */
    @Transactional
    public DeviceDtos.EnrollResponse enroll(DeviceDtos.EnrollRequest request) {
        Invite invite = inviteRepository.findByToken(request.getToken().trim().toUpperCase())
                .orElseThrow(ApiException::inviteInvalid);
        if (invite.getMode() != InviteMode.DEVICE) {
            // Это обычное приглашение участника: подсказываем человеку, что делать
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "member_invite",
                    "Это приглашение для входа в аккаунт — войдите или зарегистрируйтесь, "
                            + "и код подхватится автоматически");
        }
        if (!invite.isValid()) {
            throw ApiException.inviteInvalid();
        }
        Campaign campaign = campaignRepository.findForUpdate(invite.getCampaignId())
                .orElseThrow(ApiException::inviteInvalid);

        Instant now = Instant.now();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(invite.getDisplayName() == null || invite.getDisplayName().trim().isEmpty()
                ? "Игрок" : invite.getDisplayName().trim());
        user.setProvider(AuthProvider.LOCAL);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUserId(user.getId());
        device.setCampaignId(campaign.getId());
        device.setLabel(request.getLabel() != null && !request.getLabel().trim().isEmpty()
                ? request.getLabel().trim() : invite.getLabel());
        device.setPlatform(request.getPlatform());
        device.setInviteId(invite.getId());
        device.setEnrolledBy(invite.getCreatedBy());
        device.setCreatedAt(now);
        device.setLastSeenAt(now);
        deviceRepository.save(device);

        Member member = memberService.activate(campaign.getId(), user.getId(),
                invite.getRole(), invite.getLinkedEntityId());
        invite.setUses(invite.getUses() + 1);
        inviteRepository.save(invite);
        changeRecorder.record(campaign, ObjectType.MEMBER, member.getId(), ChangeOp.UPSERT,
                MemberPayloads.of(member), user.getId(), null, null);

        AuthDtos.TokenResponse tokens = authService.issueDeviceTokens(user, device.getId(), request.getPlatform());
        return DeviceDtos.EnrollResponse.builder()
                .user(tokens.getUser())
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .expiresIn(tokens.getExpiresIn())
                .deviceId(device.getId())
                .campaignId(campaign.getId())
                .campaignTitle(campaign.getTitle())
                .myRole(member.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DeviceDtos.DeviceDto> list(UUID campaignId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.MEMBERS_MANAGE);
        List<DeviceDtos.DeviceDto> result = new ArrayList<>();
        for (Device device : deviceRepository.findAllByCampaignIdOrderByCreatedAtDesc(campaignId)) {
            String displayName = userRepository.findById(device.getUserId())
                    .map(User::getDisplayName).orElse(null);
            result.add(DeviceDtos.DeviceDto.builder()
                    .id(device.getId())
                    .userId(device.getUserId())
                    .displayName(displayName)
                    .label(device.getLabel())
                    .platform(device.getPlatform())
                    .campaignId(device.getCampaignId())
                    .revoked(device.isRevoked())
                    .createdAt(device.getCreatedAt())
                    .lastSeenAt(device.getLastSeenAt())
                    .build());
        }
        return result;
    }

    /** Отзыв устройства: сессии этого телефона перестают работать. */
    @Transactional
    public void revoke(UUID campaignId, UUID deviceId, UUID userId) {
        Member member = permissionService.requireMember(campaignId, userId);
        permissionService.require(member, Permissions.MEMBERS_MANAGE);
        Device device = deviceRepository.findByIdAndCampaignId(deviceId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Устройство"));
        device.setRevoked(true);
        deviceRepository.save(device);
        int revoked = refreshTokenRepository.revokeByDeviceId(deviceId);
        // Участие в кампании сохраняем: у человека может быть второе устройство.
        org.slf4j.LoggerFactory.getLogger(DeviceService.class)
                .info("Устройство {} отозвано, погашено сессий: {}", deviceId, revoked);
    }
}
