package ru.dndmemory.data.remote.dto;

/** POST /api/auth/oauth/{provider}/callback */
public class OAuthCallbackRequest {

    public String code;
    public String redirectUri;
    public String state;

    public OAuthCallbackRequest(String code, String redirectUri, String state) {
        this.code = code;
        this.redirectUri = redirectUri;
        this.state = state;
    }
}
