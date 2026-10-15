package ru.dndmemory.data.remote.dto;

/**
 * Приглашение или регистрация устройства, включая PNG QR-кода в base64.
 *
 * <p>{@code mode} различает два вида QR: {@code MEMBER} — приглашение человеку,
 * {@code DEVICE} — регистрация устройства (docs/api.md, раздел 3.1).
 */
public class InviteDto {

    public String id;
    public String token;
    public String url;
    public String deepLink;
    public String qrPngBase64;
    public String role;
    public String mode;
    public String displayName;
    public String label;
    public String linkedEntityId;
    public String expiresAt;
    public int maxUses;
    public int uses;

    public boolean isDevice() {
        return "DEVICE".equalsIgnoreCase(mode);
    }
}
