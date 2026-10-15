package ru.dndmemory.data.remote.dto;

/** GET /api/invites/{token} — публичный предпросмотр, без авторизации. */
public class InvitePreviewDto {

    public boolean valid;
    public String campaignId;
    public String campaignTitle;
    public String role;
    public String linkedEntityId;
    public String linkedEntityName;
    public String expiresAt;
}
