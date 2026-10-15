package ru.dndmemory.data.remote.dto;

/** POST /api/auth/refresh, POST /api/auth/logout */
public class RefreshRequest {

    public String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
