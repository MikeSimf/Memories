package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.MemberRole;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class InviteDtos {

    private InviteDtos() {
    }

    /** Приглашение всегда одноразовое, поэтому {@code maxUses} в запросе больше нет. */
    @Data
    public static class CreateInviteRequest {
        @NotNull(message = "Укажите роль")
        private MemberRole role;

        /** Герой, к которому привяжется приглашённый. */
        private UUID linkedHeroId;

        @Min(value = 1, message = "Минимум 1 час")
        @Max(value = 8760, message = "Максимум 8760 часов")
        private Integer expiresInHours;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteDto {
        private UUID id;
        private String token;
        private String url;
        private String deepLink;
        private String qrPngBase64;
        private MemberRole role;
        private UUID linkedHeroId;
        private String linkedHeroName;
        private Instant expiresAt;
        /** Всегда 1: приглашение одноразовое. */
        private int maxUses;
        private int uses;
        private boolean revoked;
        /** false — приглашение погашено или просрочено. */
        private boolean valid;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvitePreviewDto {
        private boolean valid;
        private UUID campaignId;
        private String campaignTitle;
        private MemberRole role;
        private UUID linkedHeroId;
        private String linkedHeroName;
        private Instant expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcceptInviteResponse {
        private UUID campaignId;
        private String campaignTitle;
        private UUID memberId;
        private MemberRole role;
        private UUID linkedHeroId;
    }
}
