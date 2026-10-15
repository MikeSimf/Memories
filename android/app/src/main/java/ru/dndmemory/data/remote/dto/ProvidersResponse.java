package ru.dndmemory.data.remote.dto;

/** GET /api/auth/providers */
public class ProvidersResponse {

    public OAuthProviderDto yandex;
    public OAuthProviderDto vk;

    public boolean isEnabled(String provider) {
        OAuthProviderDto p = "vk".equalsIgnoreCase(provider) ? vk : yandex;
        return p != null && p.enabled && p.authorizeUrl != null;
    }
}
