package ru.dndmemory.data.remote.dto;

/**
 * Пользователь в ответах аутентификации и {@code /api/me}.
 *
 * <p>{@code phone} и {@code hasPassword} добавлены контрактом для входа в веб-версию:
 * по второму признаку интерфейс понимает, спрашивать ли текущий пароль.
 */
public class UserDto {

    public String id;
    public String email;
    public String displayName;
    public String avatarUrl;
    public String phone;
    public boolean hasPassword;
}
