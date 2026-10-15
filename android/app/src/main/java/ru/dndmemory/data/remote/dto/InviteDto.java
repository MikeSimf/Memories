package ru.dndmemory.data.remote.dto;

/** Приглашение, включая PNG QR-кода в base64. */
public class InviteDto {

    public String id;
    public String token;
    public String url;
    public String deepLink;
    public String qrPngBase64;
    public String role;
    public String linkedEntityId;
    public String expiresAt;
    public int maxUses;
    public int uses;
}
