package ru.dndmemory.data.remote.dto;

/** Ответ POST /api/auth/refresh (refresh-токен ротируется). */
public class TokenPairDto {

    public String accessToken;
    public String refreshToken;
    public long expiresIn;
}
