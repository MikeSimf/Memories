package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Платформа, с которой пришёл клиент: у приложения сессия живёт год. */
    public static final String PLATFORM_ANDROID = "android";

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Укажите логин")
        @Pattern(regexp = "[A-Za-z0-9_.\\-]{3,32}", message = "Логин: 3–32 символа из a–z, 0–9, _ . -")
        private String login;

        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректная почта")
        private String email;

        @NotBlank(message = "Укажите пароль")
        @Size(min = 8, max = 100, message = "Пароль от 8 до 100 символов")
        private String password;

        @NotBlank(message = "Укажите имя")
        @Size(max = 120, message = "Не длиннее 120 символов")
        private String displayName;

        /** Токен приглашения из QR: пользователь сразу становится участником кампании. */
        private String inviteToken;

        /** android — долгая сессия (365 дней); иначе 30 дней. */
        private String platform;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Укажите логин")
        private String login;

        @NotBlank(message = "Укажите пароль")
        private String password;

        private String platform;
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
    public static class VerifyEmailRequest {
        @NotBlank(message = "Нужен токен")
        private String token;
    }

    @Data
    public static class PasswordResetRequestDto {
        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректная почта")
        private String email;
    }

    @Data
    public static class PasswordResetConfirmRequest {
        @NotBlank(message = "Не передан токен")
        private String token;

        @NotBlank(message = "Укажите новый пароль")
        @Size(min = 8, max = 100, message = "Пароль от 8 до 100 символов")
        private String newPassword;
    }

    @Data
    public static class UpdateProfileRequest {
        @Size(max = 120, message = "Не длиннее 120 символов")
        private String displayName;

        private UUID avatarFileId;

        /** Почта — контакт для восстановления пароля. */
        @Email(message = "Некорректная почта")
        private String email;

        /** Телефон необязателен; пароль на него не сбрасывается. */
        private String phone;
    }

    @Data
    public static class SetPasswordRequest {
        /** Нужен только если пароль уже задан. */
        private String currentPassword;

        @NotBlank(message = "Укажите новый пароль")
        @Size(min = 8, max = 100, message = "Пароль от 8 до 100 символов")
        private String newPassword;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private String login;
        private String email;
        private boolean emailVerified;
        private String displayName;
        private String avatarUrl;
        private UUID avatarFileId;

        /** Телефон в формате +7XXXXXXXXXX, если указан в профиле. */
        private String phone;

        private boolean hasPassword;

        /** Видит обращения обратной связи. */
        private boolean admin;

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
}
