package ru.dndmemory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.Invite;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.InviteRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.InviteDtos;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Приглашение строго одноразовое: принятие его гасит. */
class InviteServiceTest {

    private InviteRepository inviteRepository;
    private CampaignRepository campaignRepository;
    private MemberRepository memberRepository;
    private ChangeRecorder changeRecorder;
    private AppProperties properties;
    private InviteService service;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        inviteRepository = mock(InviteRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        HeroRepository heroRepository = mock(HeroRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        memberRepository = mock(MemberRepository.class);
        changeRecorder = mock(ChangeRecorder.class);
        QrCodeGenerator qrCodeGenerator = mock(QrCodeGenerator.class);
        properties = mock(AppProperties.class);

        when(qrCodeGenerator.toPngBase64(any())).thenReturn("qr");
        when(properties.getPublicUrl()).thenReturn("https://dnd.example.com/");
        when(properties.getJwt()).thenReturn(new AppProperties.Jwt());

        service = new InviteService(inviteRepository, campaignRepository, heroRepository, permissionService,
                memberRepository, changeRecorder, qrCodeGenerator, properties);
        campaignId = UUID.randomUUID();
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setTitle("Курс на Фандалин");
        campaign.setRevision(5);
        return campaign;
    }

    private Invite freshInvite(String token) {
        Invite invite = new Invite();
        invite.setId(UUID.randomUUID());
        invite.setCampaignId(campaignId);
        invite.setToken(token);
        invite.setRole(MemberRole.EDITOR_HERO);
        invite.setCreatedBy(UUID.randomUUID());
        invite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invite.setMaxUses(1);
        invite.setUses(0);
        return invite;
    }

    @Test
    @DisplayName("Новое приглашение выпускается одноразовым: maxUses = 1, использований 0")
    void createsSingleUseInvite() {
        InviteDtos.InviteDto dto = service.createInternal(campaign(), UUID.randomUUID(),
                MemberRole.EDITOR_HERO, null, null);

        assertEquals(1, dto.getMaxUses());
        assertEquals(0, dto.getUses());
        assertTrue(dto.isValid());
        assertEquals("https://dnd.example.com/join/" + dto.getToken(), dto.getUrl());
    }

    @Test
    @DisplayName("Принятие приглашения гасит его: uses = 1, повторно недействительно")
    void acceptingConsumesInvite() {
        Invite invite = freshInvite("ABCDEFGH");
        when(inviteRepository.findByToken("ABCDEFGH")).thenReturn(Optional.of(invite));
        when(campaignRepository.findForUpdate(campaignId)).thenReturn(Optional.of(campaign()));
        when(memberRepository.findByCampaignIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID userId = UUID.randomUUID();
        InviteDtos.AcceptInviteResponse response = service.accept("abcdefgh", userId);

        assertEquals(campaignId, response.getCampaignId());
        assertEquals(MemberRole.EDITOR_HERO, response.getRole());
        assertEquals(1, invite.getUses());
        assertFalse(invite.isValid(), "после принятия приглашение недействительно");
        verify(inviteRepository).save(invite);

        // второе принятие того же токена — отказ
        assertThrows(ApiException.class, () -> service.accept("ABCDEFGH", UUID.randomUUID()));
    }

    @Test
    @DisplayName("Просроченное приглашение не принимается")
    void expiredInviteIsRejected() {
        Invite invite = freshInvite("ABCDEFGH");
        invite.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(inviteRepository.findByToken("ABCDEFGH")).thenReturn(Optional.of(invite));

        assertThrows(ApiException.class, () -> service.accept("ABCDEFGH", UUID.randomUUID()));
    }
}
