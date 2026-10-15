package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.InviteMode;
import ru.dndmemory.domain.MemberRole;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class InviteDtos {

    private InviteDtos() {
    }

    @Data
    public static class CreateInviteRequest {
        @NotNull(message = "Укажите роль")
        private MemberRole role;

        private UUID linkedEntityId;

        @Min(value = 1, message = "Минимум 1 час")
        @Max(value = 8760, message = "Максимум 8760 часов")
        private Integer expiresInHours;

        @Min(value = 1, message = "Минимум 1 использование")
        @Max(value = 1000, message = "Максимум 1000 использований")
        private Integer maxUses;
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
        /** MEMBER — приглашение человеку, DEVICE — QR для регистрации устройства. */
        private InviteMode mode;
        /** Имя, которое получит пользователь при регистрации устройства. */
        private String displayName;
        /** Подпись устройства: «Телефон Ани». */
        private String label;
        private UUID linkedEntityId;
        private String linkedEntityName;
        private Instant expiresAt;
        private int maxUses;
        private int uses;
        private boolean revoked;
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
        private UUID linkedEntityId;
        private String linkedEntityName;
        private InviteMode mode;
        private String displayName;
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
        private UUID linkedEntityId;
    }
}
