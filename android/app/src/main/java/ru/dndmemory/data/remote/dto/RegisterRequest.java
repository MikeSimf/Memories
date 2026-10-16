package ru.dndmemory.data.remote.dto;

/**
 * POST /api/auth/register.
 *
 * <p>{@code inviteToken} необязателен: с ним пользователь создаётся и сразу
 * становится участником кампании, приглашение гасится. {@code platform}
 * {@code android} даёт долгую сессию (refresh-токен на 365 дней).
 */
public class RegisterRequest {

    public String login;
    public String email;
    public String password;
    public String displayName;
    public String inviteToken;
    public String platform;

    public RegisterRequest(String login, String email, String password, String displayName,
                           String inviteToken, String platform) {
        this.login = login;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
        this.inviteToken = inviteToken;
        this.platform = platform;
    }
}
