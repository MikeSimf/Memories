package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.MemberRole;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    /** Что админ заполняет перед выпуском QR-кода устройства. */
    @Data
    public static class CreateEnrollmentRequest {
        @NotBlank(message = "Укажите имя игрока")
        @Size(max = 120, message = "Не длиннее 120 символов")
        private String displayName;

        @NotNull(message = "Укажите роль")
        private MemberRole role;

        private UUID linkedEntityId;

        @Min(value = 1, message = "Минимум 1 час")
        @Max(value = 8760, message = "Максимум 8760 часов")
        private Integer expiresInHours;

        @Min(value = 1, message = "Минимум 1 устройство")
        @Max(value = 50, message = "Максимум 50 устройств")
        private Integer maxUses;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String label;
    }

    /** Телефон присылает токен из QR-кода — это и есть его учётные данные. */
    @Data
    public static class EnrollRequest {
        @NotBlank(message = "Не передан токен приглашения")
        private String token;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String label;

        @Size(max = 40, message = "Не длиннее 40 символов")
        private String platform;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollResponse {
        private AuthDtos.UserDto user;
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private UUID deviceId;
        private UUID campaignId;
        private String campaignTitle;
        private String myRole;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceDto {
        private UUID id;
        private UUID userId;
        private String displayName;
        private String label;
        private String platform;
        private UUID campaignId;
        private boolean revoked;
        private Instant createdAt;
        private Instant lastSeenAt;
    }
}
