package ru.dndmemory.data.remote.dto;

/**
 * Приглашение по QR (docs/api.md, раздел 5), включая PNG QR-кода в base64.
 * Приглашение строго одноразовое: {@code maxUses} всегда 1.
 */
public class InviteDto {

    public String id;
    public String token;
    public String url;
    public String deepLink;
    public String qrPngBase64;
    public String role;
    public String linkedHeroId;
    public String linkedHeroName;
    public String expiresAt;
    public int maxUses;
    public int uses;
    public boolean revoked;
    public boolean valid;

    /** Приглашение уже использовано или отозвано — повторно не сработает. */
    public boolean isSpent() {
        return revoked || uses >= Math.max(maxUses, 1);
    }
}
