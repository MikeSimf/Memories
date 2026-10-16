package ru.dndmemory.data.remote.dto;

/**
 * POST /api/auth/login. Вход только по логину и паролю, регистр логина
 * не важен. {@code platform} {@code android} даёт сессию на 365 дней.
 */
public class LoginRequest {

    public String login;
    public String password;
    public String platform;

    public LoginRequest(String login, String password, String platform) {
        this.login = login;
        this.password = password;
        this.platform = platform;
    }
}
