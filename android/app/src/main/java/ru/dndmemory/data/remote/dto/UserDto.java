package ru.dndmemory.data.remote.dto;

/**
 * Пользователь в ответах аутентификации и {@code /api/me}.
 *
 * <p>Вход только по логину и паролю, поэтому {@code login} есть всегда,
 * а {@code hasPassword} всегда {@code true} — поле оставлено для совместимости
 * с профилем.
 */
public class UserDto {

    public String id;
    public String login;
    public String email;
    public boolean emailVerified;
    public String displayName;
    public String avatarUrl;
    public String phone;
    public boolean hasPassword;
    public String createdAt;
}
