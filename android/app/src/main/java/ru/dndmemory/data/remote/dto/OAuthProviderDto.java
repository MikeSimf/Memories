package ru.dndmemory.data.remote.dto;

/** Описание OAuth-провайдера в GET /api/auth/providers. */
public class OAuthProviderDto {

    public boolean enabled;
    public String authorizeUrl;
}
