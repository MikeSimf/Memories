package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Ответы админ-панели сброса пароля (доступ по заголовку X-Admin-Token). */
public final class AdminDtos {

    private AdminDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordResetDto {
        private UUID id;
        private String login;
        private String email;
        /** Токен из ссылки: пока SMTP не настроен, его приходится передавать вручную. */
        private String token;
        private Instant requestedAt;
        private Instant expiresAt;
    }
}
