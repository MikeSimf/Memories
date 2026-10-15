package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректная почта")
        private String email;

        @NotBlank(message = "Укажите пароль")
        @Size(min = 8, max = 100, message = "Пароль от 8 до 100 символов")
        private String password;

        @NotBlank(message = "Укажите имя")
        @Size(max = 120, message = "Не длиннее 120 символов")
        private String displayName;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Укажите почту")
        private String email;

        @NotBlank(message = "Укажите пароль")
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "Не передан refresh-токен")
        private String refreshToken;
    }

    @Data
    public static class LogoutRequest {
        @NotBlank(message = "Не передан refresh-токен")
        private String refreshToken;
    }

    @Data
    public static class OAuthCallbackRequest {
        @NotBlank(message = "Не передан код авторизации")
        private String code;

        @NotBlank(message = "Не передан redirectUri")
        private String redirectUri;

        private String state;

        /** Нужен для VK ID: провайдер требует PKCE, код-верификатор генерирует клиент. */
        private String codeVerifier;
    }

    @Data
    public static class UpdateProfileRequest {
        @Size(max = 120, message = "Не длиннее 120 символов")
        private String displayName;

        private UUID avatarFileId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private String email;
        private String displayName;
        private String avatarUrl;
        private UUID avatarFileId;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        private UserDto user;
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderDto {
        private boolean enabled;
        private String authorizeUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvidersResponse {
        private ProviderDto yandex;
        private ProviderDto vk;
    }
}
